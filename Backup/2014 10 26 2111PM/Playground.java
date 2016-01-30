import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.regex.*;
import javax.tools.*;
import java.net.*;
import java.lang.reflect.*;
import java.lang.ProcessBuilder.*;
import java.util.*;

/**
 * A text editor that acts like a playground in other XCode; you simply type code in and it'll
 * automagically run it at the push of a button. No need to make a new class, a main method, add
 * the variables, compile it, and then run. Just type and press the button, and you're done!
 */
public class Playground {
    //Overall frame
    static JSplitPane splitter;
    static JFrame frame;

    //Main editing area and related
    static JScrollPane textScroll;
    static JTextPane text;
    static StyledDocument doc;

    //Error/console output
    static JScrollPane outputScroll;
    static JTextPane outputText;
    static boolean consoleDisplayed = true;
    static TextOutputStream out, err;

    //Pre-defined "constants" for fonts
    static SimpleAttributeSet error; //Red, italics
    static SimpleAttributeSet warning; //Pink, italics
    static SimpleAttributeSet progErr; //Used only for debugging, when this program itself needs to report an error.

    //File input/output stuff.
    static File file; //The temporary file we create and delete when we're done
    static JFileChooser chooser;

    //Other miscellaneous stuff
    static JavaCompiler compiler;
    static boolean JVMrunning;
    static Process JVM;
    static IOHandlerThread redirectErr, redirectOut, redirectIn;

    //Options variables
    static double defaultSliderPosition;

    public static void main(String[] args) {
        //Main
        frame = new JFrame("Java Playground");
        frame.setSize(640, 480);
        //Make sure the divider is properly resized
        frame.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent c){splitter.setDividerLocation(defaultSliderPosition);}});
        //Make sure the JVM is reset on close
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent w){new FrameAction().kill();}});

        //Setting up the keybinding
        //Ctrl+r or Cmd+r -> compile/run
        bind(KeyEvent.VK_R);

        //Ctrl+k or Cmd+k -> kill JVM
        bind(KeyEvent.VK_K);

        //Ctrl+e or Cmd+e -> console
        bind(KeyEvent.VK_E);

        //Save, New file, Open file, Print.
        //Currently UNUSED until I figure out how normal java files and playground files will interface.
        bind(KeyEvent.VK_S);
        bind(KeyEvent.VK_N);
        bind(KeyEvent.VK_O); //Rebound to options menu for now
        bind(KeyEvent.VK_P);

        //Ctrl+h or Cmd+h -> help menu
        bind(KeyEvent.VK_SLASH);

        //Binds the keys to the action defined in the FrameAction class.
        frame.getRootPane().getActionMap().put("console",new FrameAction());

        //The main panel for typing code in.
        text = new JTextPane();
        textScroll = new JScrollPane(text);
        textScroll.setBorder(null);
        textScroll.setPreferredSize(new Dimension(640, 480));

        //Document with syntax highlighting. Currently unfinished.
        doc = text.getStyledDocument();
        doc.addDocumentListener(new DocumentListener() 
            {
                public void changedUpdate(DocumentEvent d) {}

                public void insertUpdate(DocumentEvent d) {}

                public void removeUpdate(DocumentEvent d) {}
            });

        ((AbstractDocument)doc).setDocumentFilter(new NewLineFilter());

        //The output log; a combination compiler warning/error/runtime error/output log.
        outputText = new JTextPane();
        outputScroll = new JScrollPane(outputText);
        outputScroll.setBorder(null);

        //"Constant" for the error font
        error = new SimpleAttributeSet();
        error.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
        error.addAttribute(StyleConstants.Foreground, Color.RED);

        //"Constant" for the warning message font
        warning = new SimpleAttributeSet();
        warning.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
        warning.addAttribute(StyleConstants.Foreground, Color.PINK);

        //"Constant" for the debugger error font
        progErr = new SimpleAttributeSet();
        progErr.addAttribute(StyleConstants.Foreground, Color.BLUE);

        //Print streams to redirect System.out and System.err.
        out = new TextOutputStream(outputText, null);
        err = new TextOutputStream(outputText, error);
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        //Sets up the output log
        outputText.setEditable(false);
        outputScroll.setVisible(true);

        //File input/output setup
        chooser = new JFileChooser();

        //Setting up miscellaneous stuff
        compiler = ToolProvider.getSystemJavaCompiler();
        JVMrunning = false;
        redirectErr = null;
        redirectOut = null;
        redirectIn = null;

        //Options initial values
        defaultSliderPosition = .8;

        //Sets up the splitter pane and opens the program up
        splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textScroll, outputScroll);
        consoleDisplayed = false;
        splitter.remove(outputScroll); //Initially hides terminal until it is needed
        splitter.setOneTouchExpandable(true);
        frame.add(splitter);
        frame.setVisible(true);

        //Sets the divider to the proper one, for debugging
        //splitter.setDividerLocation(.8);
    }

    //Adds a new keybinding equal to the character provided and the default super key (ctrl/cmd)
    private static void bind(int Character) {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(Character,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),"console");    
    }

    //Equivilant to System.out.println().
    private static void println(String message) {
        println(message, null);
    }

    //Default settings
    private static void print(String message) {
        print(message, null);
    }

    //Appends text to the end of the log, and adds a newline automatically.
    private static void println(String message, SimpleAttributeSet settings) {
        print(message + "\n", settings);
    }

    //Appends text to the end of the log, using the provided settings. Doesn't add a new line.
    private static void print(String message, SimpleAttributeSet settings) {
        try {
            outputText.getDocument().insertString(outputText.getDocument().getLength(), message, settings);
        } catch (BadLocationException e) {
            try {
                outputText.getDocument().insertString(outputText.getDocument().getLength(), "Couldn't insert message \"" + message + "\".", progErr);
            } catch (BadLocationException b) {
                //If you ever reach this error, something is seriously wrong, so just please swallow it and ignore it.
            }
        }
    }

    //Makes sure the log is visible.
    private static void displayLog() {
        if (!consoleDisplayed) {
            splitter.add(outputScroll);
            splitter.setDividerLocation(defaultSliderPosition);
            consoleDisplayed=true;
        }
    }

    private static class FrameAction extends AbstractAction {
        public void actionPerformed(ActionEvent a) {
            //Note that this only works on *nix OSes.
            //For windows, get the first character of the action command, cast it to int, and compare that on a case-by-case basis.

            String command = a.getActionCommand();
            if (command.equals("e")) {
                //Log toggle.
                if (consoleDisplayed = !consoleDisplayed) {
                    splitter.add(outputScroll);
                    splitter.setDividerLocation(defaultSliderPosition);
                } else {
                    splitter.remove(outputScroll);
                }
            } else if (command.equals("r")) {
                //Gets the code into a string.
                String code = text.getText();

                //Marks certain areas as "dirty".
                //Dirty areas are places that shouldn't be considered for any keywords,
                //including "import", "extend", and so on.
                ArrayList<Integer> dirtyBounds = getDirty(code);

                if (text.getText().contains("class")) {
                    //This means we should try to compile this as normal.

                    //Pulls out class name
                    int firstPos = code.indexOf("public class");
                    int secondPos = code.indexOf("{");
                    String name = code.substring(firstPos + "public class".length() + 1, secondPos).trim();

                    //Just a safety check to make sure you don't try to modify this program while it's running.
                    if (name.equals("Playground")) {
                        System.out.println("I know what you're doing and I don't approve. I won't even compile that.");
                        return;
                    }

                    compileAndRun(name, code);
                } else {
                    //This means we should compile this as a playground.

                    //Common import statements built-in
                    String importDump = new String();
                    importDump+=(//"import java.util.*;\n" + 
                        "import javax.swing.*;\n" + 
                        "import javax.swing.event.*;\n" +
                        "import java.awt.*;\n" + 
                        "import java.awt.event.*;\n" + 
                        "import java.io.*;\n");

                    //User-defined or auto-generated methods
                    String methodDump = new String();

                    //dirtyBounds.forEach(System.out::println);

                    //Pulls out any "import" statements and appends them to the import dump.
                    int i = code.indexOf("import");
                    while(i >= 0) {
                        //Ignores comments and string literals
                        if (isDirty(dirtyBounds, i)) {
                            i = code.indexOf("import", i+1);
                            continue;
                        }

                        String s = code.substring(i, code.indexOf(";", i) + 1); 
                        //System.out.println("Found import: " + s);
                        code = code.replaceFirst(s, "");
                        importDump+=s+"\n";
                        i = code.indexOf("import", i+1);
                    }

                    //Pulls out methods- these are defined by the keyword "method" until a closing bracket.
                    /*
                    i = code.indexOf("method");
                    while(i >= 0) {
                    //Ignores comments and string literals
                    if (isDirty(dirtyBounds, i)) {
                    i = code.indexOf("method", i+1);
                    continue;
                    }

                    //This scans from the first '{' until the last '}' to pull out the full method declaration.
                    char temp = 0;
                    int pos = code.indexOf("{", i+1), count = 1;
                    if(pos != -1) {
                    while(++pos < code.length()) {
                    if (count == 0) 
                    break;

                    temp = code.charAt(pos);
                    if (temp == '{') 
                    count++;
                    if (temp == '}') 
                    count--;
                    }
                    } else {
                    //Missing an opening bracket, so just remove "method" and hope it compiles.
                    code = code.replaceFirst("method", "");
                    i = code.indexOf("method", i+1);
                    continue;
                    }
                    String s = code.substring(i, pos);

                    //System.out.println("Found method: " + s);
                    code = code.replace(s, ""); 
                    methodDump+=(s.replaceFirst("method ", "static ")) + "\n";

                    i = code.indexOf("method", i+1);
                    }*/

                    //Pulls out all methods
                    i = code.indexOf("(");
                    while(i >= 0) {
                        if (isDirty(dirtyBounds,i)) {
                            i = code.indexOf("(", i+1); continue;
                        }

                        //Move backwards first
                        char temp = 0; int pos = i;
                        boolean shouldSkip = false;
                        while(--pos > 0) {
                            temp = code.charAt(pos);
                            if (temp == '.') {shouldSkip = true; break;} //This is a method call, ie String.charAt();
                            if (temp == ';') {++pos; break;} //This is most likely a method declaration, since we had no errors
                            //If we hit the start of the file, that's probable a method dec. too!
                        }
                        if (shouldSkip || isDirty(dirtyBounds, pos)) {i=code.indexOf("(", i+1); continue;} //If this def. isn't a method or it's in a comment

                        int start = pos;
                        temp = 0; pos = code.indexOf("{", i+1);
                        int count = 1;
                        if(pos != -1) {
                            while(++pos < code.length()) {
                                if (count == 0) 
                                    break;

                                temp = code.charAt(pos);
                                if (temp == '{') 
                                    count++;
                                if (temp == '}') 
                                    count--;
                            }
                        } else {
                            i = code.indexOf("(", i+1);
                            continue;
                        }

                        int end = pos;
                        String s = code.substring(start,end);
                        code = code.replace(s, ""); 
                        
                        //Just to make it look nicer
                        s = s.trim();
                        
                        System.out.println("Found method: " + s);
                        methodDump+=(s + "\n");
                        i = code.indexOf("(", i+1);
                    }

                    //Inject the class header and main method, imports, and methods
                    code = "//User and auto-imports pre-defined\n"
                    + importDump
                    + "//Autogenerated class\n"
                    + "public class Main {\n" 
                    + "public static void main(String[] args) {\n"
                    + code
                    + "}\n"
                    + methodDump 
                    + "}";

                    //Run as normal
                    compileAndRun("Main", code);
                }
            } else if (command.equals("k")) {
                kill();  
            } else if (command.equals("o")) {
                new OptionFrame(frame);
            } else if (command.equals("/")) {
                new HelpFrame(frame);
            }
        }

        //Writes the program to a source file, compiles it, and runs it.
        private void compileAndRun(String fileName, String code) {
            //Exceptions here can pick and choose what font to use, as needed.
            //Exceptions thrown by the program, that cause the Playground to be unstable, should be in blue.

            println("Deleting old temp files...", progErr);
            new File(fileName + ".java").delete();
            new File(fileName + ".class").delete();

            println("Creating source file...", progErr);
            file = new File(fileName + ".java");

            println("Writing code to source file...", progErr);
            try {
                new FileWriter(file).append(code).close();
            } catch (IOException i) {
                println("Had an IO Exception when trying to write the code. Stack trace:", error);
                i.printStackTrace();
                return; //Exit on error
            }

            println("Compiling code...", progErr);
            //This should only ever be called if the JDK isn't installed. How you'd get here, I don't know.
            if (compiler == null) {
                println("Fatal Error: JDK not installed. Go to java.sun.com and install.", error);
                return;
            }

            //Tries to compile. Success code is 0, so if something goes wrong, do stuff.
            int result = compiler.run(null, null, null, file.getAbsolutePath()); //Possibly add a new outputstream to parse through the compiler errors
            if (result != 0) {
                displayLog();
                println("Failed to compile.", error);
                return; //Return on error
            }

            println("Code compiled with 0 errors.", progErr);

            println("Attempting to run code...", progErr);
            run(fileName);
        }

        //Creates a new thread, runs the program in that thread, and reports any errors as needed.
        private void run(String clazz) {
            try {
                //Makes sure the JVM resets if it's already running.
                if(JVMrunning) 
                    kill();

                //Some String constants for java path and OS-specific separators.
                String separator = System.getProperty("file.separator");
                String path = System.getProperty("java.home")
                    + separator + "bin" + separator + "java";

                //Tries to run compiled code.
                ProcessBuilder builder = new ProcessBuilder(path, clazz);

                //Should be good now! Everything past this is on you. Don't mess it up.
                println("Build succeeded on " + java.util.Calendar.getInstance().getTime().toString(), progErr);
                println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~", progErr);                

                JVM = builder.start();

                //Note that as of right now, there is no support for input. Only output.
                Reader errorReader = new InputStreamReader(JVM.getErrorStream());
                Reader outReader = new InputStreamReader(JVM.getInputStream());
                //Writer inReader = new OutputStreamWriter(JVM.getOutputStream());

                redirectErr = redirectIOStream(errorReader, err);
                redirectOut = redirectIOStream(outReader, out);
                //redirectIn = redirectIOStream(null, inReader);

            } catch (IOException e) {
                //JVM = builder.start() can throw this.
                println("IOException when running the JVM.", progErr);
                e.printStackTrace();
                displayLog();
                return;
            }
        }

        //Kills the JVM process and any active threads on it.
        private void kill() {
            if (redirectErr != null) {
                redirectErr.close();
                redirectErr.interrupt();
            }

            if (redirectOut != null) {
                redirectOut.close();
                redirectOut.interrupt();
            }

            if (JVM != null) {
                JVM.destroy();
                JVM = null;
            }

            JVMrunning = false;

            println("JVM reset on " + java.util.Calendar.getInstance().getTime().toString(), progErr);
        }

        //Adds a new Thread that links a reader to a writer. Used to 
        private IOHandlerThread redirectIOStream(Reader reader, TextOutputStream writer) {
            IOHandlerThread thr = new IOHandlerThread(reader, writer);
            thr.start();
            return thr;
        }

        //Used in compiling to mark out "dirty" areas of the code; ie, comments and string literals.
        //These dirty areas don't get their syntax highlighted or any special treatment.
        //@returns: pairs of integers that represent dirty boundaries.
        private ArrayList<Integer> getDirty(String code) {
            ArrayList<Integer> dirtyBounds = new ArrayList<>();

            //Handles string literals
            int j = -1;
            while(true) {
                j = code.indexOf("\"", j+1);
                if (j < 0) break;
                //Ignore escaped characters
                if (!code.substring(j-1, j).equals("\\")) 
                    dirtyBounds.add(j);
            }

            //End of line comments
            j = -1;
            while(true) {
                j = code.indexOf("//", j+1);
                if (j < 0) break;
                dirtyBounds.add(j);
                //If there's no newline, then the comment lasts for the length of the code
                dirtyBounds.add(
                    code.indexOf("\n", j+1) == -1 ? code.length() : code.indexOf("\n", j+1)); 
            }

            //Block comments (and javadoc comments)
            j = -1;
            while(true) {
                j = code.indexOf("/*", j+1);
                if (j < 0) break;
                dirtyBounds.add(j);
                dirtyBounds.add(code.indexOf("*/", j+1));
            }

            return dirtyBounds;
        }

        //A helper method to check if a given position is dirty or not.
        private boolean isDirty(ArrayList<Integer> dirty, int position) {
            for (int k = 0; k < dirty.size(); k+=2) 
                if (position > dirty.get(k) && position < dirty.get(k+1)) 
                    return true;
            return false;
        }
    }

    /**
     * This is the main writer for the log. You write to either "err" or "out" to write to the log.
     * Note that System.out and System.err are also linked to this, as well as println().
     */
    private static class TextOutputStream extends OutputStream {
        final JTextPane pane;
        final SimpleAttributeSet properties;
        public TextOutputStream(JTextPane p, SimpleAttributeSet s) {
            pane = p; properties=s;
        }

        public void write(byte[] buffer, int offset, int length) {
            String text = new String(buffer, offset, length);
            SwingUtilities.invokeLater(()->{try{pane.getDocument().insertString(pane.getDocument().getLength(), text, properties);}catch(Exception e){}});
        }

        //For compatibility with writers when talking to the JVM.
        public void write(char[] buffer, int offset, int length) {
            String text = new String(buffer, offset, length);
            SwingUtilities.invokeLater(()->{try{pane.getDocument().insertString(pane.getDocument().getLength(), text, properties);}catch(Exception e){}});
        }

        public void write(int b) {
            write(new byte[]{(byte)b},0,1);
        }
    }

    /**
     * A normal ClassLoader will use the JVM cache to load files. This is good for most programs, but when you're trying to recompile and 
     * run a new program, it won't work past the first time without a JVM reset. This overrides the behavior of the default ClassLoader to
     * force the JVM to always reload the new class definition.
     */
    private static class Reloader extends ClassLoader {
        //A convenience method that works only for this program.
        public static void reload() {
            new Reloader().loadClass("Main");
        }

        public Class<?> loadClass(String s) {
            return findClass(s);
        }

        public Class<?> findClass(String s) {
            try {
                byte[] bytes = loadClassData(s);
                return defineClass(s, bytes, 0, bytes.length);
            } catch (IOException ioe) {
                try {
                    return super.loadClass(s);
                } catch (ClassNotFoundException ignore) { }
                ioe.printStackTrace(System.out);
                return null;
            }
        }

        private byte[] loadClassData(String className) throws IOException {
            Playground.println("Loading class " + className + ".class", warning);
            File f = new File(System.getProperty("user.dir") + "/" + className + ".class");
            byte[] b = new byte[(int) f.length()];
            new FileInputStream(f).read(b);
            return b;
        }
    }

    /**
     * Acts as a connection pipe between a reader and a writer. This allows a link between a JVM's output and
     * our program's input; our input is then read to the log.
     */
    private static class IOHandlerThread extends Thread {
        private Reader reader;
        private TextOutputStream writer;
        private volatile boolean keepRunning = true;
        IOHandlerThread(Reader reader, TextOutputStream writer) {
            super();
            this.reader = reader;
            this.writer = writer;
        }

        public void close() {keepRunning = false;}

        public void run()
        {
            try {
                // An arbitrary buffer size.
                char [] chbuf = new char[4096];

                while (keepRunning) {
                    int numchars = reader.read(chbuf);
                    if (numchars == -1) {
                        keepRunning = false;
                    }
                    else if (keepRunning) {
                        writer.write(chbuf, 0, numchars);
                        if (! reader.ready()) {
                            writer.flush();
                        }
                    }
                }
            }
            catch (IOException ex) {
                println("Error when linking JVM output to terminal window input.");
            }
        }
    }

    private static class NewLineFilter extends DocumentFilter {
        public void insertString(FilterBypass fb, int offs, String str, AttributeSet a)
        throws BadLocationException
        {
            if ("\n".equals(str)) 
                str = addWhiteSpace(fb.getDocument(), offs);

            super.insertString(fb, offs, str, a);
        }

        public void replace(FilterBypass fb, int offs, int length, String str, AttributeSet a)
        throws BadLocationException
        {
            if ("\n".equals(str))
                str = addWhiteSpace(fb.getDocument(), offs);

            super.replace(fb, offs, length, str, a);
        }

        private String addWhiteSpace(Document doc, int offset)
        throws BadLocationException
        {
            StringBuilder whiteSpace = new StringBuilder("\n");
            Element rootElement = doc.getDefaultRootElement();
            int line = rootElement.getElementIndex( offset );
            int i = rootElement.getElement(line).getStartOffset();

            while (true)
            {
                String temp = doc.getText(i, 1);

                if (temp.equals(" ") || temp.equals("\t"))
                {
                    whiteSpace.append(temp);
                    i++;
                }
                else
                    break;
            }

            return whiteSpace.toString();
        }
    }

    private static class OptionFrame extends JFrame {
        static JTabbedPane options;
        static JPanel op1;
        static JPanel op2;

        public OptionFrame(JFrame f) {
            super("Options");
            setSize(320, 240);
            setResizable(false);
            options = new JTabbedPane();
            op1 = new JPanel();
            op1.setLayout(new GridLayout(6,2));
            op1.add(new JLabel("Run:"));
            op1.add(new JTextField());
            op1.add(new JLabel("Reset JVM:"));
            op1.add(new JTextField());
            op1.add(new JLabel("Open options:"));
            op1.add(new JTextField());
            op1.add(new JLabel("Toggle terminal:"));
            op1.add(new JTextField());

            op2 = new JPanel();
            options.addTab("Key bindings", op1);
            options.addTab("Preferences", op2);
            add(options);
            setLocationRelativeTo(f); //Makes this pop up in the center of the frame
            setVisible(true);
        }
    }

    private static class HelpFrame extends JFrame {
        public HelpFrame(JFrame f) {
            super("Help menu");
            setSize(320, 240);
            setLocationRelativeTo(f);
            setResizable(false);

            JTextArea message = new JTextArea();
            message.setEditable(false);
            message.setText("Default keybindings:\n     -Cmd+e to toggle terminal\n     -Cmd+k to run\n     -Cmd+/ to show help\n     -Cmd+o for options\n\n" + 
                "Special keywords: \n     -'import' can be typed anywhere \n     -'method' and then a method will compile\n\n" +
                "Other: \n     -You can also use this for normal Java editing!");

            add(message, "Center");
            setVisible(true);
        }
    }
}