/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.nsvb.monkeyremote;

import com.android.chimpchat.ChimpChat;
import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.TouchPressType;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
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

    private static final String ADB = "C:\\Users\\ns130291\\Android\\sdk\\platform-tools\\adb.exe";
    private static final long TIMEOUT = 5000;

    private static float scalingFactor = 0.5f;

    private final IChimpDevice device;

    public MonkeyRemote(IChimpDevice device, int deviceWidth, int deviceHeight, BufferedImage initialScreen) {
        this.device = device;

        final int dWScaled = (int) (deviceWidth * scalingFactor);
        final int dHScaled = (int) (deviceHeight * scalingFactor);

        setTitle("MonkeyRemote");
        
        setSize(dWScaled + 50, dHScaled + 50);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                MonkeyRemote.this.device.dispose();
            }

        });

        DeviceScreen screen = new DeviceScreen(initialScreen, dWScaled, dHScaled);

        GestureListener gestureListener = new GestureListener(device);
        screen.addMouseListener(gestureListener);
        screen.addMouseMotionListener(gestureListener);

        add(screen);
        //pack();
        setVisible(true);

        int i = 1;
        while (true) {
            System.out.println("#" + i++);
            try {
                screen.setImage(device.takeSnapshot().getBufferedImage());
                screen.repaint();
            } catch (Exception ex) {
                System.out.println("Couldn't aquire screenshot: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        String adb = ADB;
        if(args.length == 2){
            adb = args[0];
            scalingFactor = Float.parseFloat(args[1]);
        } else if (args.length > 0){
            System.out.println("Usage: MonkeyRemote [Path to ADB executable] [Scaling factor]");
        }
        
        //http://stackoverflow.com/questions/6686085/how-can-i-make-a-java-app-using-the-monkeyrunner-api
        Map<String, String> options = new TreeMap<>();
        options.put("backend", "adb");
        options.put("adbLocation", adb);
        ChimpChat chimpchat = ChimpChat.getInstance(options);
        IChimpDevice device = chimpchat.waitForConnection(TIMEOUT, ".*");

        /*for (String prop : device.getPropertyList()) {
         System.out.println(prop + ": " + device.getProperty(prop));
         }*/
        device.wake();
        BufferedImage screen = device.takeSnapshot().getBufferedImage();

        int width = screen.getWidth();
        int height = screen.getHeight();

        System.out.println("Device screen dimension:" + height + "x" + width);

        MonkeyRemote remote = new MonkeyRemote(device, width, height, screen);
        //chimpchat.shutdown();
    }

    private class DeviceScreen extends JPanel {

        private BufferedImage image;
        private final int dWScaled;
        private final int dHScaled;

        public DeviceScreen(BufferedImage image, int dWScaled, int dHScaled) {
            this.image = image;
            this.dWScaled = dWScaled;
            this.dHScaled = dHScaled;
        }

        public void setImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, dWScaled, dHScaled, null);
            //setSize(dWScaled, dHScaled);
            //MonkeyRemote monkeyRemote = (MonkeyRemote) SwingUtilities.getAncestorOfClass(MonkeyRemote.class, this);
            //monkeyRemote.pack();
        }
    }

    private class GestureListener extends MouseInputAdapter {

        private boolean gestureActive = false;
        private final IChimpDevice device;
        private long lastSent = 0;
        private int lastX = 0;
        private int lastY = 0;

        public GestureListener(IChimpDevice device) {
            this.device = device;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
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
                System.out.println("DOWN " + x + " " + y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (gestureActive) {
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.UP);
                    gestureActive = false;
                    System.out.println("UP " + x + " " + y);
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (gestureActive) {
                //System.out.println("mouse dragged " + (int) (e.getX() / scalingFactor) + " " + (int) (e.getY() / scalingFactor) + " " + e.getButton());
                if (System.currentTimeMillis() - lastSent > 1) { // max every 2 milliseconds
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.MOVE);
                    lastX = x;
                    lastY = y;
                    lastSent = System.currentTimeMillis();
                    System.out.println("MOVE " + x + " " + y);
                }
            }
        }

        private void sendTouchEvent(int x, int y, TouchPressType type) {
            device.touch(x, y, type);
        }
    }

}
