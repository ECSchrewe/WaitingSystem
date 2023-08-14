package org.example;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SimGUI extends JFrame {

    private Simulation sim = Simulation.buildKaeseLaden();
    private final JEditorPane textArea;
    private final JButton nextButton;
    private final Integer[] fontSizes = {14, 16, 18, 20, 22, 24, 26, 28};
    private final int defaultSelectionIndex = 4;
    private int fontSize = fontSizes[defaultSelectionIndex];

    public SimGUI() {
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Exception e) {
            // ignore
        }
        getContentPane().setLayout(new BorderLayout());
        setDefaultCloseOperation(this.EXIT_ON_CLOSE);

        textArea = new JEditorPane("text/html", sim.getStatusHTMLOutput(fontSize));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(900, 600));
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        nextButton = new JButton("Next");
        nextButton.addActionListener(event -> {
            sim.fireNextEvent();
            updateText();
        });

        JButton kaeseLaden = new JButton("Käseladen");
        kaeseLaden.addActionListener(event -> {
            sim = Simulation.buildKaeseLaden();
            initialiseSim();
        });

        JButton kaeseLadenExtended = new JButton("Käseladen mit Probierstand");
        kaeseLadenExtended.addActionListener(event -> {
            sim = Simulation.buildKaeseLadenMitProbierstand();
            initialiseSim();
        });

        JButton fromFile = new JButton("From file");
        fromFile.addActionListener(event -> {
            JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
            fileChooser.setFileFilter(new FileNameExtensionFilter("json-files", "json", "JSON"));
            int response = fileChooser.showOpenDialog(this);
            if (response == JFileChooser.APPROVE_OPTION) {
                try {
                    String data = Files.readString(Paths.get(fileChooser.getSelectedFile().getAbsolutePath()));
                    sim = Simulation.jsonParser(data);
                    initialiseSim();
                } catch (Exception e) {
                    System.out.println("Failed to load from " + fileChooser.getSelectedFile().getAbsolutePath());
                    System.out.println(e.getMessage());
                }
            }
        });

        JComboBox<Integer> fontSizeSelection = new JComboBox<>(fontSizes);
        fontSizeSelection.setSelectedIndex(defaultSelectionIndex);
        fontSizeSelection.addActionListener(event -> {
            fontSize = (Integer) fontSizeSelection.getSelectedItem();
            updateText();
        });

        JPanel buttonArea = new JPanel(new FlowLayout());

        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(new JLabel("Font Size"));
        buttonArea.add(fontSizeSelection);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(fromFile);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(kaeseLaden);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(kaeseLadenExtended);
        buttonArea.add(Box.createHorizontalGlue());
        buttonArea.add(nextButton);
        buttonArea.add(Box.createHorizontalGlue());
        getContentPane().add(buttonArea, BorderLayout.SOUTH);
        initialiseSim();
        pack();
        setVisible(true);
    }

    private void initialiseSim() {
        sim.fireNextEvent();
        updateText();
        setTitle(sim.getTitle());
    }

    private void updateText() {
        textArea.setText(sim.getStatusHTMLOutput(fontSize));
        nextButton.setToolTipText(sim.peekNextEvent());
    }


}
