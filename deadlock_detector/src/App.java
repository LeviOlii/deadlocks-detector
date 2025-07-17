import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Optional;

public class App extends Application {

    private SistemaOperacional sistemaOperacional;
    private TextArea logArea = new TextArea();
    private ListView<String> listaProcessos = new ListView<>();
    private ListView<String> listaRecursos = new ListView<>();

    @Override
    public void start(Stage primaryStage) {
        // Solicita o intervalo Δt ao usuário
        TextInputDialog dialog = new TextInputDialog("5");
        dialog.setTitle("Intervalo de Verificação");
        dialog.setHeaderText("Informe o intervalo Δt (em segundos) para verificação de deadlock:");
        dialog.setContentText("Δt:");

        int intervalo = 5; // valor padrão
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                intervalo = Integer.parseInt(result.get());
                if (intervalo <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                log("Valor inválido para Δt. Usando 5 segundos.");
                intervalo = 5;
            }
        } else {
            log("Nenhum valor informado. Usando 5 segundos.");
            intervalo = 5;
        }

        sistemaOperacional = new SistemaOperacional(intervalo);
        sistemaOperacional.setOnUpdate(this::atualizarInterface);
        sistemaOperacional.start(); // inicia a thread do SO

        VBox root = new VBox(10);
        root.setPadding(new javafx.geometry.Insets(10));

        // Campo de entrada para novos recursos
        TextField nomeRecurso = new TextField();
        nomeRecurso.setPromptText("Nome do recurso");
        TextField idRecurso = new TextField();
        idRecurso.setPromptText("ID");
        TextField qtdRecurso = new TextField();
        qtdRecurso.setPromptText("Qtd Instâncias");

        Button btnAdicionarRecurso = new Button("Adicionar Recurso");
        btnAdicionarRecurso.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idRecurso.getText());
                int qtd = Integer.parseInt(qtdRecurso.getText());
                Recurso recurso = new Recurso(id, nomeRecurso.getText(), qtd);
                boolean adicionado = sistemaOperacional.adicionarRecurso(recurso);
                if (adicionado) {
                    atualizarInterface();
                    log("Recurso " + nomeRecurso.getText() + " adicionado.");
                } else {
                    log("Erro: ID já existe ou limite de 10 recursos atingido.");
                }
            } catch (NumberFormatException ex) {
                log("Erro: ID e Quantidade precisam ser números inteiros.");
            }
        });

        HBox recursoInputs = new HBox(5, nomeRecurso, idRecurso, qtdRecurso, btnAdicionarRecurso);

        // Campos de entrada para novo processo
        TextField idProcessoField = new TextField();
        idProcessoField.setPromptText("ID Processo");
        TextField tsField = new TextField();
        tsField.setPromptText("ΔTs (s)");
        TextField tuField = new TextField();
        tuField.setPromptText("ΔTu (s)");

        Button btnCriarProcesso = new Button("Criar Processo");
        btnCriarProcesso.setOnAction(e -> {
            try {
                int id = Integer.parseInt(idProcessoField.getText());
                int ts = Integer.parseInt(tsField.getText());
                int tu = Integer.parseInt(tuField.getText());
                if (ts <= 0 || tu <= 0) throw new NumberFormatException();

                // Verifica limite de 10 processos
                if (sistemaOperacional.getProcessos().size() >= 10) {
                    log("Erro: Limite de 10 processos atingido.");
                    return;
                }
                // Verifica se já existe processo com o mesmo ID
                boolean idExistente = sistemaOperacional.getProcessos().stream()
                    .anyMatch(proc -> proc.getProcessoId() == id);
                if (idExistente) {
                    log("Erro: Já existe um processo com o ID informado.");
                    return;
                }

                Processo p = new Processo(id, ts, tu, sistemaOperacional, this::log);
                sistemaOperacional.adicionarProcesso(p);
                p.start();
                atualizarInterface();
                log("Processo " + id + " criado (ΔTs=" + ts + "s, ΔTu=" + tu + "s).");
            } catch (NumberFormatException ex) {
                log("Erro: ID, ΔTs e ΔTu devem ser inteiros positivos.");
            }
        });

        HBox processoInputs = new HBox(5, idProcessoField, tsField, tuField, btnCriarProcesso);

        // Botões para criar e eliminar processos
        TextField idEliminarField = new TextField();
        idEliminarField.setPromptText("ID para eliminar");

        Button btnEliminarProcesso = new Button("Eliminar Processo");
        btnEliminarProcesso.setOnAction(e -> {
            try {
                int idEliminar = Integer.parseInt(idEliminarField.getText());
                Processo p = sistemaOperacional.getProcessos().stream()
                    .filter(proc -> proc.getProcessoId() == idEliminar)
                    .findFirst()
                    .orElse(null);
                if (p != null) {
                    p.interrupt();
                    sistemaOperacional.removerProcesso(p);
                    log("Processo " + idEliminar + " eliminado.");
                    atualizarInterface();
                } else {
                    log("Erro: Processo com ID " + idEliminar + " não encontrado.");
                }
            } catch (NumberFormatException ex) {
                log("Erro: Informe um ID válido para eliminar.");
            }
        });

        HBox botoesProcesso = new HBox(10, idEliminarField, btnEliminarProcesso);

        // Área de log
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(200);

        // Layout final
        root.getChildren().addAll(
                new Label("Adicionar Recurso:"),
                recursoInputs,
                new Label("Recursos:"),
                listaRecursos,
                new Label("Adicionar Processo:"),
                processoInputs,
                new Label("Processos:"),
                listaProcessos,
                botoesProcesso,
                new Label("Log:"),
                logArea
        );

        primaryStage.setScene(new Scene(root, 600, 600));
        primaryStage.setTitle("Sistema de Detecção de Deadlock");
        primaryStage.show();
    }

    public void atualizarInterface() {
        Platform.runLater(() -> {
            listaRecursos.getItems().setAll(sistemaOperacional.statusRecursos());
            listaProcessos.getItems().setAll(sistemaOperacional.statusProcessos());
        });
    }

    public void log(String msg) {
        Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
        });
    }

    @Override
    public void stop() {
        // Interrompe todos os processos
        for (Processo p : sistemaOperacional.getProcessos()) {
            p.interrupt();
        }
        // Interrompe o sistema operacional
        sistemaOperacional.interrupt();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
