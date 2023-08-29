import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Stack;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/*
TODO: Idee – Logging mit reinnehmen (z. B. beim Einspielen des Backups, Wechseln des Modus etc.)

TODO: Idee – Erstellen der `setStorageValue`-Kommandos für alle Variablen! Entweder auf CLI oder in separates Textfile!
*/

public class ScreenshotApp extends JFrame {
    private static GraphicsConfiguration graphicsConfiguration;
    private BufferedImage originalScreenshotImage;
    private BufferedImage scaledScreenshotImage;
    private BufferedImage croppedScreenshotImage;
    private BufferedImage annotatedImage;
    private BufferedImage annotatedImageWithClickPosition;
    private final Stack<BufferedImage> annotatedImageBackups = new Stack<>();
    private Rectangle selectionRect;
    // Container for the image icon
    private final JLabel screenshotLabel;
    // Container for the actual image
    private final ImageIcon icon;
    private String outputFilename;
    private int argScreenshotWaitTime;
    private int argScaledWidth;
    private int argScaledHeight;
    private String argSaveLocationPath;
    private String argWindowName;
    private int argScreenNumber;
    private boolean autoreadWindowName = true;
    private ApplicationState applicationState;
    private CommandOutputMode commandOutputMode;
    private final Component screenshotApp = this;

    private enum CommandOutputMode {
        excel,
        mateoscript
    }

    private enum ApplicationState {
        Crop,
        Annotate
    }

    /**
     * Startet die App
     * <p>
     * `ESC` beendet, <code>N<code> nimmt ein neues Bild auf, ein Klick generiert das nötige Kommando, Drag-and-Drop um zuzuschneiden
     *
     * @param args 0: Wartezeit in Sekunden vor der Aufnahme eines neuen Screenshots (bspw. <code>2</code>)<br><br>
     *             1: Breite des skalierten Screenshots (bspw. <code>1920</code>)<br><br>
     *             2: Höhe des skalierten Screenshots (bspw. <code>1080</code>)<br><br>
     *             3: Speicherpfad für die erzeugten Screenshots (bspw. <code>"C:\Pfad mit Leerzeichen\"</code><br><br>
     *             4: Name des Fensters (bspw. <code>"§windowName§"</code> um Flexibilität für mateo zu erhalten)<br><br>
     *             5: Bildschirm, der abfotografiert werden soll und auf dem das Programm angezeigt wird (bspw. <code>1</code>)
     *             6: Ausgabemodus: <code>excel</code> oder <code>mateoScript</code>
     */
    public ScreenshotApp(String[] args) {

        super("ScreenShot App", graphicsConfiguration);

        initStartParams(args);
        showParamsDialog(true);

        icon = new ImageIcon();
        screenshotLabel = new JLabel(icon);
        this.add(screenshotLabel);

        initCropMode(0.2);

        // TODO: Maybe add scaling, resizing etc. on the fly
        /*addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
            }
        });*/

        addKeyListener(createCropModeKeyAdapter());

        MouseAdapter mouseAdapter = createMouseAdapter();

        screenshotLabel.addMouseListener(mouseAdapter);
        screenshotLabel.addMouseMotionListener(mouseAdapter);
    }

    private void initStartParams(String[] args) {
        try {
            argScreenshotWaitTime = Integer.parseInt(args[0]);
            argScaledWidth = Integer.parseInt(args[1]);
            argScaledHeight = Integer.parseInt(args[2]);
            argSaveLocationPath = normalizePath(args[3] + "/");
            argWindowName = args[4];
            commandOutputMode = CommandOutputMode.valueOf(args[6].toLowerCase());
            argScreenNumber = Integer.parseInt(args[5]);
        } catch (Exception e) {
            System.out.println("Problem mit den Parametern!");
            e.printStackTrace();
            initDefaultStartParams();
        }
    }

    private void initDefaultStartParams() {
        System.out.println("Lade default Parameter");
        argScreenshotWaitTime = 2;
        argScaledWidth = 1280;
        argScaledHeight = 720;
        argSaveLocationPath = new JFileChooser().getFileSystemView().getDefaultDirectory().toString();
        argWindowName = "§windowName§";
        commandOutputMode = CommandOutputMode.excel;
        argScreenNumber = getNumberOfDefaultScreen();
    }


    private int getNumberOfDefaultScreen(){
        int result = 0;
        GraphicsDevice defaultGraphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsDevice[] graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        for (int i = 0; i < graphicsDevices.length; i++) {
            if(graphicsDevices[i].equals(defaultGraphicsDevice)){
                result = i;
            }
        }
        return result;
    }

    private String normalizePath(String path) {
        return path.replaceAll("(\\\\+|/+)", "/");
    }

    private void showParamsDialog(boolean quitApplicationOnCancel) {
        JDialog dialog = new JDialog(this, "Startparameter", true);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if(quitApplicationOnCancel)
                    System.exit(0);
            }
        });

        JLabel screenshotWaitTimeLabel = new JLabel("Wartezeit in Sekunden");
        JTextField screenshotWaitTimeTextField = new JTextField();
        screenshotWaitTimeTextField.setText(String.valueOf(argScreenshotWaitTime));

        JLabel scaledWidthLabel = new JLabel("Breite des Screenshots");
        JTextField scaledWidthTextField = new JTextField();
        scaledWidthTextField.setText(String.valueOf(argScaledWidth));

        JLabel scaledHeightLabel = new JLabel("Höhe des Screenshots");
        JTextField scaledHeightTextField = new JTextField();
        scaledHeightTextField.setText(String.valueOf(argScaledHeight));

        JLabel saveLocationPathLabel = new JLabel("Speicherpfad");
        JTextField saveLocationPathTextField = new JTextField();
        saveLocationPathTextField.setText(argSaveLocationPath);
        JButton openDirSelectorButton = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        openDirSelectorButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setCurrentDirectory(new File(argSaveLocationPath));
            int option = fileChooser.showOpenDialog(dialog);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                saveLocationPathTextField.setText(file.getAbsolutePath());
            }
        });

        JLabel windowNameLabel = new JLabel("Name des Fensters");
        JTextField windowNameTextField = new JTextField();
        JCheckBox windowNameCheckBox = new JCheckBox("automatisch Erkennen", autoreadWindowName);
        windowNameTextField.setText(argWindowName);

        JLabel screenNumberLabel = new JLabel("Nummer des Bildschirms");
        JTextField screenNumberTextField = new JTextField();
        screenNumberTextField.setText(String.valueOf(argScreenNumber));

        JLabel commandOutputModeLabel = new JLabel("Ausgabemodus");
        JComboBox<CommandOutputMode> commandOutputModeComboBox = new JComboBox<>(CommandOutputMode.values());
        commandOutputModeComboBox.setSelectedItem(commandOutputMode);

        ActionListener continueActionListener = actionEvent -> {
            try {
                argScreenshotWaitTime = Integer.parseInt(screenshotWaitTimeTextField.getText());
                argScaledWidth = Integer.parseInt(scaledWidthTextField.getText());
                argScaledHeight = Integer.parseInt(scaledHeightTextField.getText());
                argSaveLocationPath = normalizePath(saveLocationPathTextField.getText() + "/");
                argWindowName = windowNameTextField.getText();
                autoreadWindowName = windowNameCheckBox.isSelected();
                commandOutputMode = (CommandOutputMode) commandOutputModeComboBox.getSelectedItem();
                argScreenNumber = Integer.parseInt(screenNumberTextField.getText());
                graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[argScreenNumber].getDefaultConfiguration();
                dialog.dispose();
            } catch (Exception e) {
                System.out.println("Problem mit den Parametern!");
                e.printStackTrace();
                System.exit(1);
            }
        };

        JButton continueButton = new JButton("Continue");
        JButton cancelButton = new JButton("Cancel");

        continueButton.addActionListener(continueActionListener);
        cancelButton.addActionListener(actionEvent -> System.exit(0));

        JPanel buttonsPanel = new JPanel();
        FlowLayout flowLayout = new FlowLayout();
        flowLayout.setHgap(10);
        buttonsPanel.setLayout(flowLayout);
        buttonsPanel.add(continueButton);
        buttonsPanel.add(cancelButton);

        JPanel pane = new JPanel(new GridBagLayout());
        pane.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(1, 1, 1, 1);
        pane.add(screenshotWaitTimeLabel, c);

        c.gridx = 1;
        c.gridy = 0;
        pane.add(screenshotWaitTimeTextField, c);

        c.gridx = 0;
        c.gridy = 1;
        pane.add(scaledWidthLabel, c);

        c.gridx = 1;
        c.gridy = 1;
        pane.add(scaledWidthTextField, c);

        c.gridx = 0;
        c.gridy = 2;
        pane.add(scaledHeightLabel, c);

        c.gridx = 1;
        c.gridy = 2;
        pane.add(scaledHeightTextField, c);

        c.gridx = 0;
        c.gridy = 3;
        pane.add(saveLocationPathLabel, c);

        c.gridx = 1;
        c.gridy = 3;
        c.weighty = 1;
        pane.add(saveLocationPathTextField, c);

        c.gridx = 2;
        c.gridy = 3;
        c.fill = GridBagConstraints.NONE;
        c.weighty = 0;
        pane.add(openDirSelectorButton, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 4;
        pane.add(windowNameLabel, c);

        c.gridx = 1;
        c.gridy = 4;
        pane.add(windowNameTextField, c);

        c.gridx = 2;
        c.gridy = 4;
        pane.add(windowNameCheckBox, c);

        c.gridx = 0;
        c.gridy = 5;
        pane.add(screenNumberLabel, c);

        c.gridx = 1;
        c.gridy = 5;
        pane.add(screenNumberTextField, c);

        c.gridx = 0;
        c.gridy = 6;
        pane.add(commandOutputModeLabel, c);

        c.gridx = 1;
        c.gridy = 6;
        pane.add(commandOutputModeComboBox, c);

        c.gridx = 0;
        c.gridy = 7;
        c.gridwidth = 3;
        pane.add(buttonsPanel, c);

        dialog.setContentPane(pane);
        packCenterAndShowMainWindow(dialog);
    }

    /**
     * Takes a screenshot after waiting `waitTime` seconds
     *
     * @param waitTime Wait time in seconds
     */
    private void initCropMode(double waitTime) {

        applicationState = ApplicationState.Crop;

        // Wait before taking screenshot
        try {
            Thread.sleep((long) (waitTime * 1000L));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Actual taking of the screenshot
        try {
            Rectangle bounds = graphicsConfiguration.getBounds();
            if(autoreadWindowName)
                argWindowName = getCurrentWindowText();
            originalScreenshotImage = new Robot().createScreenCapture(new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height));
        } catch (AWTException | SecurityException e) {
            e.printStackTrace();
        }

        // Set everything to default values
        croppedScreenshotImage = new BufferedImage(originalScreenshotImage.getWidth(), originalScreenshotImage.getHeight(), originalScreenshotImage.getType());
        copyImageBuffer(originalScreenshotImage, croppedScreenshotImage);

        scaledScreenshotImage = new BufferedImage(argScaledWidth, argScaledHeight, originalScreenshotImage.getType());
        copyImageBuffer(originalScreenshotImage, scaledScreenshotImage, argScaledWidth, argScaledHeight);

        icon.setImage(scaledScreenshotImage);
        screenshotLabel.repaint();
        packCenterAndShowMainWindow(this);
    }

    private String getCurrentWindowText(){
        char[] buffer = new char[1024];
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        User32.INSTANCE.GetWindowText(hwnd, buffer, 1024);
        return Native.toString(buffer);
    }

    KeyAdapter createCropModeKeyAdapter() {
        return new KeyAdapter() {
            public void keyPressed(KeyEvent ke) {

                if (ke.getKeyCode() == KeyEvent.VK_N) {
                    System.out.println("Warte " + argScreenshotWaitTime + " Sekunde(n).");
                    setVisible(false);
                    initCropMode(argScreenshotWaitTime);
                }

                if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    int result = JOptionPane.showConfirmDialog(ScreenshotApp.this, "Programm verlassen?", "Screenshot-Tool",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    if (result == JOptionPane.YES_OPTION) {
                        System.exit(0);
                    }
                }

                int down = KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK;
                if ((ke.getModifiersEx() & down) == down && (ke.getKeyCode() == KeyEvent.VK_S)){
                    showParamsDialog(false);
                    initCropMode(0.2);
                }

                if (ke.getKeyCode() == KeyEvent.VK_U && !annotatedImageBackups.empty()) {
                    croppedScreenshotImage = annotatedImageBackups.pop();
                    // Both calls are required for the image to update; `icon`'s `setImage` doesn't point to the buffer but seems to copy its contents instead!
                    icon.setImage(croppedScreenshotImage);
                    screenshotLabel.repaint();
                }
            }
        };
    }

    MouseAdapter createMouseAdapter() {
        return new MouseAdapter() {
            private Point startPoint;

            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();

                // TODO: Append to list of backups, instead of just having a single backup
                if (applicationState == ApplicationState.Annotate) {
                    BufferedImage tmp = new BufferedImage(croppedScreenshotImage.getWidth(), croppedScreenshotImage.getHeight(), croppedScreenshotImage.getType());

                    annotatedImageBackups.push(tmp);
                    copyImageBuffer(croppedScreenshotImage, tmp);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {

                // TODO: Refactor. Could be pulled into separate method. The whole "annotatedImageWithClickPosition" stuff only really needs to happen once the decision to save was made!

                // If there was no dragging, it was a simple click
                if (Point2D.distance(startPoint.x, startPoint.y, e.getX(), e.getY()) == 0) {

                    Point center = new Point(screenshotLabel.getWidth() / 2, screenshotLabel.getHeight() / 2);

                    // Create image with green cross-hair for click position
                    annotatedImageWithClickPosition = new BufferedImage(croppedScreenshotImage.getWidth(), croppedScreenshotImage.getHeight(), croppedScreenshotImage.getType());
                    copyImageBuffer(croppedScreenshotImage, annotatedImageWithClickPosition);

                    Graphics2D g2d = annotatedImageWithClickPosition.createGraphics();
                    g2d.setColor(Color.decode("#00FF00"));
                    g2d.setStroke(new BasicStroke(3));
                    g2d.drawLine(e.getX() - 5, e.getY(), e.getX() + 5, e.getY());
                    g2d.drawLine(e.getX(), e.getY() - 5, e.getX(), e.getY() + 5);
                    g2d.dispose();

                    showSaveDialog();

                    // Only generate mateoScript command string if image was actually saved
                    if (outputFilename == null) {
                        return;
                    }

                    String outputString = createCommandOutput(e.getPoint().x - center.x, e.getPoint().y - center.y);
                    System.out.println(outputString);

                    Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .setContents(
                                    new StringSelection(outputString),
                                    null
                            );

                    JOptionPane.showMessageDialog(screenshotApp,
                            "Der Befehl wurde in die Zwischenablage kopiert.\n" +
                                    "Die Screenshots wurden in " + argSaveLocationPath + " gespeichert.",
                            "Info",
                            JOptionPane.PLAIN_MESSAGE);

                    // Since we only CLICKED, we don't need to handle what happens when we DRAGGED
                    return;
                }

                double scalingFactor = originalScreenshotImage.getWidth() / (double) argScaledWidth;

                switch (applicationState) {
                    case Crop:
                        croppedScreenshotImage = originalScreenshotImage.getSubimage(
                                (int) (selectionRect.x * scalingFactor), (int) (selectionRect.y * scalingFactor),
                                (int) (selectionRect.width * scalingFactor), (int) (selectionRect.height * scalingFactor));
                        icon.setImage(croppedScreenshotImage);
                        screenshotLabel.setSize(croppedScreenshotImage.getWidth(), croppedScreenshotImage.getHeight());
                        screenshotLabel.repaint();
                        packCenterAndShowMainWindow(ScreenshotApp.this);
                        applicationState = ApplicationState.Annotate;
                        break;
                    case Annotate:
                        // "Burns" the annotation into the image
                        croppedScreenshotImage = annotatedImage;
                        icon.setImage(croppedScreenshotImage);
                        screenshotLabel.repaint();
                        break;
                    default:
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // Makes sure we can drag in negative directions, too. Credit to https://stackoverflow.com/a/30942094.
                int x = Math.min(startPoint.x, e.getX());
                int y = Math.min(startPoint.y, e.getY());
                int width = Math.abs(startPoint.x - e.getX());
                int height = Math.abs(startPoint.y - e.getY());

                selectionRect = new Rectangle(x, y, width, height);

                BufferedImage baseImage;
                if (applicationState == ApplicationState.Crop) {
                    baseImage = scaledScreenshotImage;
                } else {
                    baseImage = croppedScreenshotImage;
                }
                annotatedImage = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), baseImage.getType());
                Graphics2D g2d = annotatedImage.createGraphics();
                g2d.drawImage(baseImage, 0, 0, null);
                switch (applicationState) {
                    case Crop:
                        g2d.setColor(Color.RED);
                        g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4}, 0));
                        g2d.draw(selectionRect);
                        break;
                    case Annotate:
                        g2d.setColor(Color.decode("#FF00FF"));
                        g2d.fill(selectionRect);
                        break;
                }
                g2d.dispose();
                icon.setImage(annotatedImage);
                screenshotLabel.repaint();
            }
        };
    }

    private String createCommandOutput(int x, int y){
        String blankoCommand = commandOutputMode == CommandOutputMode.mateoscript ?
                "clickImageWin(WINDOW_NAME = \"%s\", IMAGE_RELATIVE_PATH = \"%s\", BASEDIR = \"%s\", RELATIVE_X = \"%s\", RELATIVE_Y = \"%s\")"
                :
                "0\tclickImageWin\t%s\t%s\t%s\t\t\t%s\t%s";
        String outputString = String.format(blankoCommand, argWindowName, outputFilename, argSaveLocationPath, x, y);
        return outputString.replaceAll("\t", "|");
    }

    private void showSaveDialog() {
        JDialog dialog = new JDialog(this, "Dateiname eingeben", true);
        JTextField textField = new JTextField(20);
        String timestamp = String.valueOf(new Date().getTime());
        textField.setText(timestamp);
        textField.selectAll();
        JLabel label = new JLabel(".png");
        JPanel textFieldPanel = new JPanel(new BorderLayout());
        textFieldPanel.add(textField, BorderLayout.WEST);
        textFieldPanel.add(label, BorderLayout.EAST);
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        // Make sure we set this, so we don't try to generate a mateo string unless a file name has been chosen.
        outputFilename = null;

        ActionListener saveActionListener = actionEvent -> {
            File outputfile = new File(argSaveLocationPath + textField.getText() + ".png");
            System.out.println("Datei wird gespeichert unter: " + outputfile);
            try {
                ImageIO.write(croppedScreenshotImage, "png", outputfile);
                ImageIO.write(annotatedImageWithClickPosition, "png", new File(outputfile + "_click_position.png"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Once this is set, we can generate a mateo command string!
            outputFilename = textField.getText() + ".png";
            annotatedImageBackups.clear();
            dialog.dispose();
        };

        // Add a key binding to the dialog's root pane to dispose the dialog when the "Escape" key is pressed
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        dialog.getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        saveButton.addActionListener(saveActionListener);
        // Gets triggered on "Enter" key being pressed
        textField.addActionListener(saveActionListener);

        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout());
        buttonsPanel.add(saveButton);
        buttonsPanel.add(cancelButton);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(textFieldPanel, BorderLayout.NORTH);
        contentPanel.add(buttonsPanel, BorderLayout.SOUTH);

        dialog.setContentPane(contentPanel);
        packCenterAndShowMainWindow(dialog);
    }


    /**
     * Directly writes INTO toBuffer, thus it can be used as normally afterwards!
     *
     * @param fromBuffer Buffer to copy from
     * @param toBuffer   Buffer to copy to
     */
    private void copyImageBuffer(BufferedImage fromBuffer, BufferedImage toBuffer, int dstWidth, int dstHeight) {
        Graphics2D g = toBuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(fromBuffer, 0, 0,
                dstWidth, dstHeight, 0, 0,
                fromBuffer.getWidth(),
                fromBuffer.getHeight(), null);
        g.dispose();
    }

    private void copyImageBuffer(BufferedImage fromBuffer, BufferedImage toBuffer) {
        copyImageBuffer(fromBuffer, toBuffer, fromBuffer.getWidth(), fromBuffer.getHeight());
    }

    private void packCenterAndShowMainWindow(Window c) {
        c.pack();
        c.setLocation((int) (graphicsConfiguration.getBounds().getLocation().x + graphicsConfiguration.getBounds().getWidth() / 2 - c.getWidth() / 2),
                (int) (graphicsConfiguration.getBounds().getLocation().y + graphicsConfiguration.getBounds().getHeight() / 2 - c.getHeight() / 2));
        c.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                int screenNumber = Integer.parseInt(args[5]);
                graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[screenNumber].getDefaultConfiguration();
            }catch (ArrayIndexOutOfBoundsException e){
                System.out.println("Problem beim Lesen der screenNumber. Wert bleibt default");
                graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            }

            ScreenshotApp app = new ScreenshotApp(args);
            app.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        });
    }
}