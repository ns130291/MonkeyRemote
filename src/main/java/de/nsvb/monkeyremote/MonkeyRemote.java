/*
 * Copyright (C) 2016-2022 ns130291
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.nsvb.monkeyremote;

import com.android.chimpchat.ChimpChat;
import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.TouchPressType;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.event.MouseInputAdapter;

/**
 *
 * @author ns130291
 */
public class MonkeyRemote extends JFrame {

    private static final String ADB = "C:\\adb\\adb.exe";
    private static final long TIMEOUT = 5000;

    private static String[] args;

    private static float scalingFactor = 0.5f;
    private static String adb = "";

    private IChimpDevice device;
    private ChimpChat chimpchat;
    private boolean reconnect = false;

    private IChimpDevice connectDevice() {
        //http://stackoverflow.com/questions/6686085/how-can-i-make-a-java-app-using-the-monkeyrunner-api
        Map<String, String> options = new TreeMap<>();
        options.put("backend", "adb");
        options.put("adbLocation", adb);
        chimpchat = ChimpChat.getInstance(options);
        return chimpchat.waitForConnection(TIMEOUT, ".*");
    }

    public MonkeyRemote() {

        device = connectDevice();

        if (device == null) {
            System.err.println("Error: Couldn't connect to device");
            return;
        }

        /*for (String prop : device.getPropertyList()) {
         System.out.println(prop + ": " + device.getProperty(prop));
         }*/
        device.wake();
        BufferedImage initialScreen = device.takeSnapshot().getBufferedImage();

        int deviceWidth = initialScreen.getWidth();
        int deviceHeight = initialScreen.getHeight();

        System.out.println("Device screen dimension:" + deviceWidth + "x" + deviceHeight);


        int dWScaled = (int) (deviceWidth * scalingFactor);
        int dHScaled = (int) (deviceHeight * scalingFactor);

        setTitle("MonkeyRemote");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                MonkeyRemote.this.device.dispose();
            }

        });

        DeviceScreen screen = new DeviceScreen(initialScreen, dWScaled, dHScaled);

        GestureListener gestureListener = new GestureListener(device, this);
        screen.addMouseListener(gestureListener);
        screen.addMouseMotionListener(gestureListener);

        add(screen);
        pack();
        setVisible(true);

        int i = 1;
        int error_counter = 0;
        while (true) {
            if (error_counter > 10 || reconnect) {
                restart();
            }
            System.out.println("#" + i++);
            try {
                BufferedImage screenImage = device.takeSnapshot().getBufferedImage();
                if (screenImage.getWidth() != deviceWidth || screenImage.getHeight() != deviceHeight) {
                    deviceWidth = screenImage.getWidth();
                    deviceHeight = screenImage.getHeight();
                    dWScaled = (int) (deviceWidth * scalingFactor);
                    dHScaled = (int) (deviceHeight * scalingFactor);
                    screen.updateScreenSize(dWScaled, dHScaled);
                    pack();
                }
                screen.setImage(screenImage);
                screen.repaint();
                if (error_counter > 0) {
                    error_counter--;
                }
            } catch (Exception ex) {
                error_counter++;
                System.out.println("Couldn't aquire screenshot: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        MonkeyRemote.args = args;
        adb = ADB;
        if (args.length == 2) {
            adb = args[0];
            scalingFactor = Float.parseFloat(args[1]);
            args = new String[0];
        }
        if (args.length > 0 || !new File(adb).exists() || new File(adb).isDirectory()) {
            if (!new File(adb).exists()) {
                System.err.println("Error: ADB executable wasn't found at \"" + adb + "\"");
            }
            if (new File(adb).isDirectory()) {
                System.err.println("Error: Path to ADB executable is a directory");
            }
            System.out.println("Usage: MonkeyRemote [Path to ADB executable] [Scaling factor]");
            return;
        }
        new MonkeyRemote();
    }

    private class DeviceScreen extends JPanel {

        private BufferedImage image;
        private int dWScaled;
        private int dHScaled;

        public DeviceScreen(BufferedImage image, int dWScaled, int dHScaled) {
            this.image = image;
            this.dWScaled = dWScaled;
            this.dHScaled = dHScaled;
            setPreferredSize(new Dimension(dWScaled, dHScaled));
        }

        public void setImage(BufferedImage image) {
            this.image = image;
        }

        public void updateScreenSize(int dWScaled, int dHScaled) {
            this.dWScaled = dWScaled;
            this.dHScaled = dHScaled;
            setPreferredSize(new Dimension(dWScaled, dHScaled));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, dWScaled, dHScaled, null);
        }
    }

    private class GestureListener extends MouseInputAdapter {

        private boolean gestureActive = false;
        private final IChimpDevice device;
        private final MonkeyRemote mr;
        private long lastSent = 0;
        private int lastX = 0;
        private int lastY = 0;

        public GestureListener(IChimpDevice device, MonkeyRemote mr) {
            this.device = device;
            this.mr = mr;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1 && !mr.reconnect) {
                if (gestureActive) {
                    sendTouchEvent(lastX, lastY, TouchPressType.UP);
                    System.out.println("UP, cancelling old gesture " + lastX + " " + lastY);
                }
                int x = (int) (e.getX() / scalingFactor);
                int y = (int) (e.getY() / scalingFactor);
                gestureActive = true;
                lastX = x;
                lastY = y;
                sendTouchEvent(x, y, TouchPressType.DOWN);
                //System.out.println("DOWN " + x + " " + y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1 && !mr.reconnect) {
                if (gestureActive) {
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.UP);
                    gestureActive = false;
                    //System.out.println("UP " + x + " " + y);
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (gestureActive && !mr.reconnect) {
                //System.out.println("mouse dragged " + (int) (e.getX() / scalingFactor) + " " + (int) (e.getY() / scalingFactor) + " " + e.getButton());
                if (System.currentTimeMillis() - lastSent > 1) { // max every 2 milliseconds
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.MOVE);
                    lastX = x;
                    lastY = y;
                    lastSent = System.currentTimeMillis();
                    //System.out.println("MOVE " + x + " " + y);
                }
            }
        }

        private void sendTouchEvent(int x, int y, TouchPressType type) {
            try {
                switch (type) {
                    case DOWN:
                        device.getManager().touchDown(x, y);
                        break;
                    case UP:
                        device.getManager().touchUp(x, y);
                        break;
                    case DOWN_AND_UP:
                        device.getManager().tap(x, y);
                        break;
                    case MOVE:
                        device.getManager().touchMove(x, y);
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                mr.reconnect = true;
                gestureActive = false;
            }
        }
    }

    // https://stackoverflow.com/a/48992863
    private static void restart() {
        List<String> command = new ArrayList<>(32);
        appendJavaExecutable(command);
        appendVMArgs(command);
        appendClassPath(command);
        appendEntryPoint(command);
        appendArgs(command, args);

        System.out.println(command);
        try {
            new ProcessBuilder(command).inheritIO().start();
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Restart failed");
        }
        System.exit(0);
    }

    private static void appendJavaExecutable(List<String> cmd) {
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }

    private static void appendVMArgs(Collection<String> cmd) {
        Collection<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();

        String javaToolOptions = System.getenv("JAVA_TOOL_OPTIONS");
        if (javaToolOptions != null) {
            Collection<String> javaToolOptionsList = Arrays.asList(javaToolOptions.split(" "));
            vmArguments = new ArrayList<>(vmArguments);
            vmArguments.removeAll(javaToolOptionsList);
        }

        cmd.addAll(vmArguments);
    }

    private static void appendClassPath(List<String> cmd) {
        cmd.add("-cp");
        cmd.add(ManagementFactory.getRuntimeMXBean().getClassPath());
    }

    private static void appendEntryPoint(List<String> cmd) {
        StackTraceElement[] stackTrace          = new Throwable().getStackTrace();
        StackTraceElement   stackTraceElement   = stackTrace[stackTrace.length - 1];
        String              fullyQualifiedClass = stackTraceElement.getClassName();
        String              entryMethod         = stackTraceElement.getMethodName();
        if (!entryMethod.equals("main"))
            throw new AssertionError("Entry point is not a 'main()': " + fullyQualifiedClass + '.' + entryMethod);

        cmd.add(fullyQualifiedClass);
    }

    private static void appendArgs(List<String> cmd, String[] args) {
        cmd.addAll(Arrays.asList(args));
    }

}
