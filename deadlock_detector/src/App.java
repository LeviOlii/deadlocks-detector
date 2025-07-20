import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class App extends Application {

    private SistemaOperacional sistemaOperacional;
    private TextArea logArea = new TextArea();
    private ListView<String> listaProcessos = new ListView<>();
    private ListView<String> listaTodosRecursos = new ListView<>();
    private ListView<String> listaRecursosDisponiveis = new ListView<>();
    private TextArea matrizAlocacao = new TextArea();
    private TextArea matrizRequisicao = new TextArea();
    private AtomicLong lastUpdateTime = new AtomicLong(0);
    private static final long UPDATE_INTERVAL_MS = 500; // 500ms throttling

    @Override
    public void start(Stage primaryStage) {
        TextInputDialog dialog = new TextInputDialog("5");
        dialog.setTitle("Intervalo de Verificação");
        dialog.setHeaderText("Informe o intervalo Δt (em segundos) para verificação de deadlock:");
        dialog.setContentText("Δt:");

        int intervalo = 5;
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
        }

        sistemaOperacional = new SistemaOperacional(intervalo);
        sistemaOperacional.setOnUpdate(this::atualizarInterface);
        sistemaOperacional.setLogger(this::log);
        sistemaOperacional.start();

        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new javafx.geometry.Insets(10));
        leftPanel.setPrefWidth(400);

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
                if (qtd <= 0) throw new NumberFormatException();
                Recurso recurso = new Recurso(id, nomeRecurso.getText(), qtd);
                boolean adicionado = sistemaOperacional.adicionarRecurso(recurso);
                if (adicionado) {
                    atualizarInterface();
                }
            } catch (NumberFormatException ex) {
                log("Erro: ID e Quantidade precisam ser números inteiros positivos.");
            } catch (Exception ex) {
                log("Erro ao adicionar recurso: " + ex.getMessage());
            }
        });

        HBox recursoInputs = new HBox(5, nomeRecurso, idRecurso, qtdRecurso, btnAdicionarRecurso);

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
                double ts = Double.parseDouble(tsField.getText());
                double tu = Double.parseDouble(tuField.getText());
                if (ts <= 0 || tu <= 0) {
                    log("Erro: ΔTs e ΔTu devem ser números positivos.");
                    return;
                }
                if (ts < 0.5) {
                    log("Erro: ΔTs deve ser pelo menos 0.5 segundos para evitar sobrecarga.");
                    return;
                }
                if (sistemaOperacional.getProcessos().size() >= 10) {
                    log("Erro: Limite de 10 processos atingido.");
                    return;
                }
                boolean idExistente = sistemaOperacional.getProcessos().stream()
                    .anyMatch(proc -> proc.getProcessoId() == id);
                if (idExistente) {
                    log("Erro: Já existe um processo com o ID informado.");
                    return;
                }

                Processo p = new Processo(id, (int)(ts * 1000), (int)(tu * 1000), sistemaOperacional, this::log);
                sistemaOperacional.adicionarProcesso(p);
                p.start();
                atualizarInterface();
            } catch (NumberFormatException ex) {
                log("Erro: ID, ΔTs e ΔTu devem ser números válidos.");
            } catch (Exception ex) {
                log("Erro ao criar processo: " + ex.getMessage());
            }
        });

        HBox processoInputs = new HBox(5, idProcessoField, tsField, tuField, btnCriarProcesso);

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
                    atualizarInterface();
                } else {
                    log("Erro: Processo com ID " + idEliminar + " não encontrado.");
                }
            } catch (NumberFormatException ex) {
                log("Erro: Informe um ID válido para eliminar.");
            } catch (Exception ex) {
                log("Erro ao eliminar processo: " + ex.getMessage());
            }
        });

        HBox botoesProcesso = new HBox(10, idEliminarField, btnEliminarProcesso);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(400);

        leftPanel.getChildren().addAll(
                new Label("Adicionar Recurso:"),
                recursoInputs,
                new Label("Todos os Recursos:"),
                listaTodosRecursos,
                new Label("Recursos Disponíveis:"),
                listaRecursosDisponiveis,
                new Label("Adicionar Processo:"),
                processoInputs,
                new Label("Processos:"),
                listaProcessos,
                botoesProcesso,
                new Label("Log do Sistema:"),
                logArea
        );

        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new javafx.geometry.Insets(10));
        rightPanel.setPrefWidth(400);

        matrizAlocacao.setEditable(false);
        matrizAlocacao.setPrefHeight(200);
        matrizAlocacao.setWrapText(false);
        matrizAlocacao.setFont(Font.font("Monospaced", 12));

        matrizRequisicao.setEditable(false);
        matrizRequisicao.setPrefHeight(200);
        matrizRequisicao.setWrapText(false);
        matrizRequisicao.setFont(Font.font("Monospaced", 12));

        rightPanel.getChildren().addAll(
                new Label("Matriz de Alocação:"),
                matrizAlocacao,
                new Label("Matriz de Requisição:"),
                matrizRequisicao
        );

        HBox root = new HBox(10);
        root.setPadding(new javafx.geometry.Insets(10));
        root.getChildren().addAll(leftPanel, rightPanel);

        primaryStage.setScene(new Scene(root, 900, 700));
        primaryStage.setTitle("Sistema de Detecção de Deadlock");
        primaryStage.show();
    }

    public void atualizarInterface() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime.get() < UPDATE_INTERVAL_MS) {
            return;
        }
        lastUpdateTime.set(currentTime);
        Platform.runLater(() -> {
            try {
                listaTodosRecursos.getItems().setAll(
                    sistemaOperacional.getRecursos().stream()
                        .map(r -> r.getNome() + " (ID: " + r.getId() + ", Total: " + r.getTotal() + ")")
                        .toList()
                );
                listaRecursosDisponiveis.getItems().setAll(sistemaOperacional.statusRecursos());
                listaProcessos.getItems().setAll(sistemaOperacional.statusProcessos());
                matrizAlocacao.setText(sistemaOperacional.getAllocationMatrixString());
                matrizRequisicao.setText(sistemaOperacional.getRequestMatrixString());
                log("Interface atualizada às " + currentTime);
            } catch (Exception ex) {
                log("Erro ao atualizar interface: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    public void log(String msg) {
        Platform.runLater(() -> {
            try {
                logArea.appendText(msg + "\n");
                System.out.println("Log: " + msg);
            } catch (Exception ex) {
                System.out.println("Erro ao registrar log: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void stop() {
        try {
            for (Processo p : sistemaOperacional.getProcessos()) {
                p.interrupt();
            }
            sistemaOperacional.interrupt();
            log("Sistema encerrado.");
        } catch (Exception ex) {
            log("Erro ao encerrar: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}