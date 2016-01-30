import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
public class TransparentFrame extends JFrame {
    Image background;
    Point latestPos;

    public static void main() {
        TransparentFrame t = new TransparentFrame();
        t.setSize(640, 480);
        t.setVisible(true);
    }

    public TransparentFrame() {
        super();
        updateBackground();    
        new Timer(5, new ActionListener() {
            public void actionPerformed(ActionEvent a) {
                System.out.println("Repainting");
                repaint();
            }
        }).start();

        addWindowListener(new WindowAdapter() {
                public void windowDeactivated(WindowEvent w) {
                    //Update background intelligently
                    updateBackground();
                }
            });
        latestPos = new Point(0,0);
    }

    public void updateBackground() {
        try {
            boolean visible = isVisible();
            Robot r = new Robot();
            Toolkit tk = Toolkit.getDefaultToolkit();
            Dimension dim = tk.getScreenSize();

            setVisible(false);
            background = r.createScreenCapture(
                new Rectangle(0,0,(int)dim.getWidth(),(int)dim.getHeight()));
            setVisible(visible);
        } catch (Exception e) {}
    }
    
    public void paint(Graphics g) {
        if(getLocationOnScreen().equals(latestPos)){return;}
        Point pos = getLocationOnScreen();
        Point offset=  new Point(-pos.x,-pos.y);
        System.out.println("Repainting to " + pos.x + ", " + pos.y);
        g.drawImage(background,offset.x,offset.y,null);
        latestPos = pos;
    }
}