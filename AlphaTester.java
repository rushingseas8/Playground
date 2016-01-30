import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AlphaTester {
    static Color alpha = new Color(0,0,0,20);
    public static void main() {

        MotionFrame frame = new MotionFrame("Test");
        frame.setUndecorated(true);
        //frame.setUndecorated(false);
        frame.setBackground(alpha);
        //frame.add(new MotionPanel(frame,"Playground alpha test"),BorderLayout.NORTH);

        JTextPane t1 = new JTextPane(), t2 = new JTextPane();
        JScrollPane s1 = new JScrollPane(t1), s2 = new JScrollPane(t2);
        t1.setBackground(alpha);
        t2.setBackground(alpha);
        s1.setBackground(alpha);
        s1.setBorder(null);
        s2.setBackground(alpha);
        s2.setBorder(null);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, s1, s2);
        split.setBackground(alpha);
        split.setBorder(null);
        frame.add(split);
        frame.setSize(640, 480);
        frame.setVisible(true);
        split.setDividerLocation(.5);
    }

    private static class MotionFrame extends JFrame {
        private Point initialClick;
        protected static boolean ignoreDrag;

        public MotionFrame() {
            this("");
        }

        public MotionFrame(String s) {
            super(s);
            ignoreDrag = false;
            MotionPanel panel = new MotionPanel(this, s);
            add(panel,BorderLayout.NORTH);

            //Global mouse listener
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                    public void eventDispatched(AWTEvent event) {
                        if(event instanceof MouseEvent){
                            MouseEvent evt = (MouseEvent)event;
                            if(evt.getID() == MouseEvent.MOUSE_CLICKED){
                                initialClick = evt.getPoint();
                            }
                        }
                    }
                }, AWTEvent.MOUSE_EVENT_MASK);

            //Global mouse drag listener
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                    public void eventDispatched(AWTEvent event) {
                        if(!(event instanceof MouseEvent)) return;

                        MouseEvent evt = (MouseEvent)event;
                        if(evt.getID() == MouseEvent.MOUSE_DRAGGED) {
                            int width = getWidth();
                            int height = getHeight();
                            int x = evt.getXOnScreen() - getX(); //Absolute minus the component's absolute.
                            int y = evt.getYOnScreen() - getY(); //Gives relative coordinates to the origin of this JFrame.
                            final int offset = 20; //Offset due to insets.

                            ignoreDrag = true;
                            if (x < offset && y < offset) { //NW
                            }
                            else if (x < offset && y > height - offset) { //NE
                            }
                            else if (x > width - offset && y < offset) { //SW
                            }
                            else if (x > width - offset && y > height - offset) { //SE
                                setSize(Math.abs(x-getX()),Math.abs(y-getY()));
                            }
                            else {
                                ignoreDrag = false;
                            }

                        }

                    }
                }, AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }

        private static class MotionPanel extends JPanel{
            private Point initialClick;
            private JFrame parent;

            public MotionPanel(final JFrame parent, String title) {
                this.parent = parent;
                add(new JLabel(title));
                setBackground(alpha);

                addMouseListener(new MouseAdapter() {
                        public void mousePressed(MouseEvent e) {
                            initialClick = e.getPoint();
                            getComponentAt(initialClick);
                        }
                    });

                addMouseMotionListener(new MouseMotionAdapter() {
                        public void mouseDragged(MouseEvent e) {
                            if(ignoreDrag) return;
                            System.out.println("Mouse dragged");

                            // get location of Window
                            int thisX = parent.getLocation().x;
                            int thisY = parent.getLocation().y;

                            // Determine how much the mouse moved since the initial click
                            int xMoved = (thisX + e.getX()) - (thisX + initialClick.x);
                            int yMoved = (thisY + e.getY()) - (thisY + initialClick.y);

                            // Move window to this position
                            int X = thisX + xMoved;
                            int Y = thisY + yMoved;
                            parent.setLocation(X, Y);
                        }
                    });
            }
        }
    }
}