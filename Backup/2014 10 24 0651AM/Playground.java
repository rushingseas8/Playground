import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.tools.*;
import java.net.*;
import java.lang.reflect.*;
import java.lang.ProcessBuilder.*;

/**
 * A text editor that acts like a playground in other languages; you simply type code in and it'll
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
    static Document doc;

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
    static boolean JVMrunning;
    static Process JVM;
    static IOHandlerThread redirectErr, redirectOut, redirectIn;

    public static void main() {
        //Main
        frame = new JFrame("Java Playground");
        frame.setSize(640, 480);
        //Make sure the divider is properly resized
        frame.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent c){splitter.setDividerLocation(.8);}});
        //Make sure the JVM is reset on close
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent w){new FrameAction().kill();}});

        //Setting up the keybinding
        //Ctrl+k or Cmd+k -> compile
        bind(KeyEvent.VK_K);

        //Ctrl+e or Cmd+e -> console
        bind(KeyEvent.VK_E);

        //Save, New file, Open file, Print.
        //Currently UNUSED until I figure out how normal java files and playground files will interface.
        bind(KeyEvent.VK_S);
        bind(KeyEvent.VK_N);
        bind(KeyEvent.VK_O);
        bind(KeyEvent.VK_P);

        //Binds the keys to the action defined in the FrameAction class.
        frame.getRootPane().getActionMap().put("console",new FrameAction());    

        //The main panel for typing code in.
        text = new JTextPane();
        textScroll = new JScrollPane(text);
        textScroll.setBorder(null);
        textScroll.setPreferredSize(new Dimension(640, 480));

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

        //Just displaying all possible fonts.
        /*
        println("Java playground version 1.0", null);
        println("This is a warning", warning);
        println("Error: This is an error", error);
        println("You really messed up now.", progErr);*/

        //Sets up the output log
        outputText.setEditable(false);
        //outputText.setVisible(consoleDisplayed = false);
        outputScroll.setVisible(true);

        //File input/output setup
        chooser = new JFileChooser();

        //Setting up miscellaneous stuff
        JVMrunning = false;
        redirectErr = null;
        redirectOut = null;
        redirectIn = null;

        //Sets up the splitter pane and opens the program up
        splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textScroll, outputScroll);
        splitter.setOneTouchExpandable(true);
        frame.add(splitter);
        frame.setVisible(true);

        //Sets the divider to the proper one, for debugging
        splitter.setDividerLocation(.8);
    }

    //Adds a new keybinding equal to the character provided and the default super key (ctrl/cmd)
    private static void bind(int Character) {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(Character,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),"console");    
    }

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

    //Essentially links an InputStream to the proper channels in the log box.
    private static void printOutput(InputStream in, SimpleAttributeSet set) {
        int i = 0;
        String s = "";
        try {
            while((i = in.read()) != -1) {
                s+=(char)i;
                //print(, set);
            }
        } catch (IOException io) {
            println("Error: IOException when reading InputStream " + in.toString(), progErr);
        }
        println(s);
    }

    //This is used for when the user's code has something wrong.
    //This has checks in place to make some more sense of the error messages.
    //Internal errors should be logged using println and the progErr font.
    private static void logError(String message) {
        if (message.contains("Playground$FrameAction")) {
            //This is a reflection error, so that means that we have a malformed class or method.
            String code = text.getText();
            if (!code.startsWith("public class")) {
                println("Error: You defined a private class. Please use \"public class <classname>\".", error);
            } else {
                println("Error: Malformed method. Make sure your main method is defined as \"public static void main(<any args>)\".", error);
            }
        } else {
            println(message, error);
        }
    }

    private static void displayLog() {
        if (!consoleDisplayed) {
            splitter.add(outputScroll);
            splitter.setDividerLocation(.8);
            consoleDisplayed=true;
        }
    }

    private static class FrameAction extends AbstractAction {
        public void actionPerformed(ActionEvent a) {
            String command = a.getActionCommand();
            if (command.equals("e")) {
                //Log toggle.
                if (consoleDisplayed = !consoleDisplayed) {
                    splitter.add(outputScroll);
                    splitter.setDividerLocation(.8);
                } else {
                    splitter.remove(outputScroll);
                }
            } else if (command.equals("k")) {
                //Insert compiling code here.
                //System.out.println("Compile");
                if(JVMrunning) {
                    //System.out.println("JVM is running!");
                    kill();
                }

                String trimmedCode = text.getText().trim().replaceAll(" ","").replaceAll("\n","").replaceAll("\t","");
                //String dir  = System.getProperty("user.dir");
                if (trimmedCode.contains("class")) {
                    //This means we should try to compile this as normal.

                    //Pulls out class name
                    String code = text.getText();
                    int firstPos = code.indexOf("class");
                    int secondPos = code.indexOf("{");
                    String name = code.substring(firstPos + "class".length() + 1, secondPos).trim();

                    compileAndRun(name, text.getText());
                } else {
                    //This means we should compile this as a playground.
                    String code = text.getText();
                    String importDump = new String();
                    importDump+="import java.util.*;\nimport javax.swing.*;\nimport java.awt.*;\nimport java.awt.event.*;\nimport java.io.*;\n";

                    code = "//Common imports pre-defined\n" + 
                    importDump + 
                    "//Autogenerated class\npublic class Main {\npublic static void main(String[] args) {\n"
                    + code
                    + "\n}\n}";

                    compileAndRun("Main", code);
                }
            }
        }

        private void compileAndRun(String fileName, String code) {
            //Exceptions here can pick and choose what font to use, as needed.
            //Exceptions thrown by the program, that cause this program to be unstable, should be in blue.

            println("Deleting old temp files...");
            new File(fileName + ".java").delete();
            new File(fileName + ".class").delete();

            println("Creating source file...");
            file = new File(fileName + ".java");

            println("Writing code to source file...");
            try {
                new FileWriter(file).append(code).close();
            } catch (IOException i) {
                println("Had an IO Exception when trying to write the code. Stack trace:", progErr);
                i.printStackTrace();
                return; //Exit on error
            }

            println("Compiling code...");
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
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

            println("Attempting to run code...");

            /*
            try {

            Method[] methods = Class.forName(fileName, false, new Reloader()).getDeclaredMethods();
            Method main = null;
            //Object[] paraList = null;
            for (int i = 0; i < methods.length; i++) {
            if(methods[i].getName().equals("main")) {
            main = methods[i];

            /*
            Class[] c = methods[i].getParameterTypes();
            paraList = new Object[c.length];
            for (int k = 0; k < c.length; k++) {
            paraList[k] = c[i].cast(new Object());
            }
            }
            }

            if (main == null) {
            println("The code specified didn't have a main method. Aborting.", error);
            return;
            }

            //Tries to call the static main method.
            run(fileName);

            //println("Done!");
            } catch (Exception e) {
            //This can only be a program error, never a user error; print this in blue.
            println(e.toString(), progErr); 

            //Pulls up the log when there's an error. Always.
            displayLog();

            return; //Exit on error
            }*/

            run(fileName);
        }

        /*
         * Creates a new thread, runs the program in that thread, and reports any errors as needed.
         */
        private void run(String clazz) {

            try {

                String separator = System.getProperty("file.separator");
                String path = System.getProperty("java.home")
                    + separator + "bin" + separator + "java";

                ProcessBuilder builder = new ProcessBuilder(path, clazz);

                //Should be good now! Everything past this is on you. Don't mess it up.
                println("Build succeeded. Run commencing!");
                println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");                

                JVM = builder.start();

                //Note that as of right now, there is no support for input. Only output.
                Reader errorReader = new InputStreamReader(JVM.getErrorStream());
                Reader outReader = new InputStreamReader(JVM.getInputStream());
                //Writer inReader = new OutputStreamWriter(JVM.getOutputStream());

                redirectErr = redirectIOStream(errorReader, err);
                redirectOut = redirectIOStream(outReader, out);
                //redirectIn = redirectIOStream(null, inReader);

                //JVM.waitFor();

                /*
                new Thread() {
                public void run() {
                try {
                JVM = builder.start();
                //JVM.waitFor();

                /*
                InputStream is = JVM.getInputStream();
                int value;
                while ((value = is.read()) != -1) {
                char inChar = (char)value;
                System.out.print(inChar);
                }
                JVMrunning = true;
                } catch (Exception e){
                e.printStackTrace();
                System.out.println("Error when trying to start JVM");
                }
                }
                }.start();*/

                //main.invoke(null, (Object) new String[]{});
                //println("Invoked method");
            } catch (NullPointerException n) {
                //println("Error: No static main method. Check that your main method is defined as \"public static void main(<any args>)\".",error);
                //} catch (InvocationTargetException i) {
                //This is the place where we deal with most user runtime errors, possibly decyphering them.

                n.printStackTrace();
                //  println("Error: " + i.getCause().toString(), error);
                //  i.getCause().printStackTrace();
            } catch (Exception e) {
                //This catches any other errors we might get.
                println("Other error thrown");
                logError(e.toString());
                displayLog();
                return;
            }
        }

        /*
         * Kills the JVM process.
         */
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
        }

        private IOHandlerThread redirectIOStream(Reader reader, TextOutputStream writer) {
            IOHandlerThread thr = new IOHandlerThread(reader, writer);
            thr.start();
            return thr;
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
}