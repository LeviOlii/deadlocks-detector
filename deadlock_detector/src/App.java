import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.geometry.Insets;

import java.util.Optional;

public class App extends Application {

    private SistemaOperacional sistemaOperacional;
    private TextArea logArea = new TextArea();
    private ListView<String> listaProcessos = new ListView<>();
    private ListView<String> listaTodosRecursos = new ListView<>();
    private ListView<String> listaRecursosDisponiveis = new ListView<>();
    private TextArea matrizAlocacao = new TextArea();
    private TextArea matrizRequisicao = new TextArea();
    private Label cronometroLabel = new Label("0s");
    private long startTime = 0;
    private Timeline timeline;

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
                if (intervalo <= 0)
                    throw new NumberFormatException();
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

        cronometroLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (startTime > 0) {
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                cronometroLabel.setText(elapsedSeconds + "s");
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        // leiaute
        GridPane root = new GridPane();
        root.setPadding(new Insets(10));
        root.setHgap(10);
        root.setVgap(10);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        root.getColumnConstraints().addAll(col1, col2);

        // lado esq: recursos, vetores e matrizes
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));

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
                if (qtd <= 0)
                    throw new NumberFormatException();
                Recurso recurso = new Recurso(id, nomeRecurso.getText(), qtd);
                boolean adicionado = sistemaOperacional.adicionarRecurso(recurso);
                if (adicionado) {
                    atualizarInterface();
                    log("Recurso " + nomeRecurso.getText() + " adicionado.");
                } else {
                    log("Erro: ID já existe ou limite de 10 recursos atingido.");
                }
            } catch (NumberFormatException ex) {
                log("Erro: ID e Quantidade precisam ser números inteiros positivos.");
            }
        });

        HBox recursoInputs = new HBox(5, new Label("Recurso:"), nomeRecurso, idRecurso, qtdRecurso,
                btnAdicionarRecurso);

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
                if (ts <= 0 || tu <= 0)
                    throw new NumberFormatException();

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

                if (sistemaOperacional.getProcessos().isEmpty()) {
                    startTime = System.currentTimeMillis();
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

        HBox processoInputs = new HBox(5, new Label("Processo:"), idProcessoField, tsField, tuField, btnCriarProcesso);

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

        HBox botoesProcesso = new HBox(10, new Label("Eliminar:"), idEliminarField, btnEliminarProcesso);

        listaProcessos.setPrefHeight(150);
        listaTodosRecursos.setPrefHeight(100);
        listaRecursosDisponiveis.setPrefHeight(100);

        leftPane.getChildren().addAll(
                new Label("Adicionar Recurso:"),
                recursoInputs,
                new Label("Todos os Recursos:"),
                listaTodosRecursos,
                new Label("Recursos Disponíveis:"),
                listaRecursosDisponiveis,
                new Label("Matriz de Alocação:"),
                matrizAlocacao,
                new Label("Matriz de Requisição:"),
                matrizRequisicao);

        // lado direito: log e processos
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));

        matrizAlocacao.setEditable(false);
        matrizAlocacao.setPrefHeight(100);
        matrizAlocacao.setWrapText(false);
        matrizAlocacao.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 12;");

        matrizRequisicao.setEditable(false);
        matrizRequisicao.setPrefHeight(100);
        matrizRequisicao.setWrapText(false);
        matrizRequisicao.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 12;");

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(350);

        rightPane.getChildren().addAll(
                new Label("Adicionar Processo:"),
                processoInputs,
                new Label("Log do Sistema:"),
                logArea,
                new Label("Tempo de Execução:"),
                cronometroLabel,
                new Label("Processos:"),
                listaProcessos,
                botoesProcesso);

        root.add(leftPane, 0, 0);
        root.add(rightPane, 1, 0);

        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.setTitle("Sistema de Detecção de Deadlock");
        primaryStage.show();
    }

    public void atualizarInterface() {
        Platform.runLater(() -> {
            listaTodosRecursos.getItems().setAll(
                    sistemaOperacional.getRecursos().stream()
                            .map(r -> r.getNome() + " (ID: " + r.getId() + ", Total: " + r.getTotal() + ")")
                            .toList());
            listaRecursosDisponiveis.getItems().setAll(sistemaOperacional.statusRecursos());
            listaProcessos.getItems().setAll(sistemaOperacional.statusProcessos());
            matrizAlocacao.setText(sistemaOperacional.getAllocationMatrixString());
            matrizRequisicao.setText(sistemaOperacional.getRequestMatrixString());
        });
    }

    public void log(String msg) {
        Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
        });
    }

    @Override
    public void stop() {
        for (Processo p : sistemaOperacional.getProcessos()) {
            p.interrupt();
        }
        sistemaOperacional.interrupt();
    }

    public static void main(String[] args) {
        launch(args);
    }
}