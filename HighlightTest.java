import javax.swing.*;
import java.awt.*;
import javax.swing.text.*;

public class HighlightTest {
    public static void main() {
        
        JFrame frame = new JFrame();
        JTextPane text = new JTextPane();
        JScrollPane scroll = new JScrollPane(text);
        frame.add(scroll);
        frame.setSize(640, 480);
        frame.setVisible(true);

        DefaultHighlighter.DefaultHighlightPainter highlightPainter = 
            new DefaultHighlighter.DefaultHighlightPainter(new Color());
        try {
            text.getHighlighter().addHighlight(0, 100, 
                highlightPainter);    
        } catch (Exception e) {
        }
    }
}