/*
 * To-do log:
 *  Add ability to parse compiler errors and work through them
 *  "Lazy" variable creation (ie, start working with 'i' without defining it first)
 *  Options for syntax highlighting
 *  Support for public/private inner classes?
 *  Undo/redo support
 *  Find/replace support
 */

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
 * A simplified Java working environment- no need to have a lot of the confusing constructs of the full language.
 */
public class Playground {
    //Overall frame
    static JSplitPane splitter;
    static JFrame frame;

    //Main editing area and related
    static JScrollPane textScroll;
    static JTextPane text;
    //static StyledDocument doc;

    //Error/console output
    static JScrollPane outputScroll;
    static JTextPane outputText;
    static boolean consoleDisplayed = true;
    static TextOutputStream out, err;
    static String lastErrorMessage = ""; //Used to parse compiler errors

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
    static ArrayList<Integer> dirtyBounds;

    //Options variables
    static double defaultSliderPosition;
    static boolean isVerticalSplitterPane;
    static boolean isFlippedSplitterPane;
    static boolean verboseCompiling;
    static boolean warningsEnabled;
    static String compileOptions;
    static String runOptions;

    public static void main(String[] args) {
        //Main
        frame = new JFrame("Java Playground");
        frame.setSize(640, 480);
        //Make sure the divider is properly resized
        frame.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent c){splitter.setDividerLocation(defaultSliderPosition);}});
        //Make sure the JVM is reset on close
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent w){kill();}});
        frame.setLocationRelativeTo( null);

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

        //Ctrl+/ or Cmd+/ -> help menu
        bind(KeyEvent.VK_SLASH);

        //Binds the keys to the action defined in the FrameAction class.
        frame.getRootPane().getActionMap().put("console",new FrameAction());

        //The main panel for typing code in.
        text = new JTextPane();
        textScroll = new JScrollPane(text);
        textScroll.setBorder(null);
        textScroll.setPreferredSize(new Dimension(640, 480));

        //Gives the document syntax highlighting.
        ((AbstractDocument)text.getStyledDocument()).setDocumentFilter(new HighlightFilter());

        //The output log; a combination compiler warning/error/runtime error/output log.
        outputText = new JTextPane();
        outputScroll = new JScrollPane(outputText);
        outputScroll.setBorder(null);
        //This makes the output log always scroll to the bottom when it gets new data. Doesn't always work.
        DefaultCaret caret = (DefaultCaret)outputText.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

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
        //Note that this means System.out.println() redirects to the output log,
        //both in this program and any program it executes.
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

        //Default options
        defaultSliderPosition = .8;
        isVerticalSplitterPane = false;
        verboseCompiling = true; //When this is enabled, make sure to disable error filtering.
        warningsEnabled = true; 
        compileOptions = "";
        runOptions = "";

        //Try to load settings from a file.
        if (new File("settings.txt").exists()) {
            loadSettings();
        } else {
            println("Couldn't find settings file. Creating new one.", warning);
            saveSettings();
        }

        //Quotes on startup.
        final String[] quotes = new String[] {
                "If you use this program to code itself, I wish you luck on your journey\n\t-George, October 26, 2014",
                "Now I am become death, the destroyer of worlds\n\t-Robert Oppenheimer, July 16, 1945",
                "Words - so innocent and powerless as they are, how potent for good and evil they become in " +
                "the hands of one who knows how to combine them.\n\t-Nathaniel Hawthorn",
                "The true sign of intelligence is not knowledge, but imagination.\n\t-Albert Einstein",
                "loop zoop\n\t-internet",
                "Ding the Bell! Neeeyh! See! Neeeyh!\n\t-TotalBiscut",
                "It's only a game.  Why you heff to be med?\n\t-Some Hockey Guy",
                "This is full of undocumented features.\n\t-Adam Rzadkowski",
                "\"null\" would this throw a null-pointer exception, George?\n\t-Adam Rzadkowski",
                ">If you're happy and you know it, segfault!\nSegmentation fault. Core dumped.\n\t-Linux shell"
            };
        println(quotes[(int)(Math.random() * quotes.length)], warning);        

        //Sets up the splitter pane and opens the program up
        splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textScroll, outputScroll);
        if(!isVerticalSplitterPane) splitter.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        consoleDisplayed = false;
        //splitter.remove(outputScroll); 
        splitter.setOneTouchExpandable(true);
        frame.add(splitter);
        frame.setVisible(true);

        splitter.setDividerLocation(.999); //Initially hides terminal until it is needed
    }

    //Saves the current settings to a file named "settings.txt".
    private static void saveSettings() {
        try {
            File settings = new File("settings.txt");
            FileWriter writer = new FileWriter(settings);
            writer.append(defaultSliderPosition + "\n");
            writer.append(isVerticalSplitterPane + "\n");
            writer.append(verboseCompiling + "\n");
            writer.append(warningsEnabled + "\n");
            writer.append(compileOptions + "\n");
            writer.append(runOptions + "\n");
            writer.close();            
        } catch (IOException i) {
            println("IO exception when saving settings.", progErr);
        }
    }

    //Loads the settings from "settings.txt", with error handling
    private static void loadSettings() {
        try {
            File settings = new File("settings.txt");
            BufferedReader reader = new BufferedReader(new FileReader(settings));
            defaultSliderPosition = Double.parseDouble(reader.readLine());
            isVerticalSplitterPane = Boolean.parseBoolean(reader.readLine());
            verboseCompiling = Boolean.parseBoolean(reader.readLine());
            warningsEnabled = Boolean.parseBoolean(reader.readLine());
            compileOptions = reader.readLine();
            runOptions = reader.readLine();
            reader.close();            
        } catch (FileNotFoundException f) {
            println("Couldn't find the settings. How the hell.", progErr);
        } catch (IOException i) {
            println("General IO exception when loading settings.", progErr);
        } catch (Exception e) {
            println("Catastrophic failure when loading settings.", progErr);
            println("Don't mess with the settings file, man!", progErr);
        }
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
            if(warning.equals(settings) && !warningsEnabled) return; //Living life on the edge, here.
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
            //splitter.add(outputScroll);
            splitter.setDividerLocation(defaultSliderPosition);
            consoleDisplayed=true;
        }
    }

    private static void updateSplitterOrientation() {
        if(isVerticalSplitterPane) splitter.setOrientation(JSplitPane.VERTICAL_SPLIT);
        else splitter.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
    }

    //Does what it says on the tin.
    private static void compileAndRun(String fileName, String code) {
        compile(fileName, code);
    }

    //Writes the program to a source file, and compiles it.
    private static void compile(String fileName, String code) {
        //Exceptions here can pick and choose what font to use, as needed.
        //Exceptions thrown by the program, that cause the Playground to be unstable, should be in blue.

        if(verboseCompiling) println("Deleting old temp files...", warning);
        new File(fileName + ".java").delete();
        new File(fileName + ".class").delete();

        if(verboseCompiling) println("Creating source file...", progErr);
        file = new File(fileName + ".java");

        if(verboseCompiling) println("Writing code to source file...", progErr);
        try {
            new FileWriter(file).append(code).close();
        } catch (IOException i) {
            println("Had an IO Exception when trying to write the code. Stack trace:", error);
            i.printStackTrace();
            return; //Exit on error
        }

        if(verboseCompiling) println("Compiling code...", progErr);
        //This should only ever be called if the JDK isn't installed. How you'd get here, I don't know.
        if (compiler == null) {
            println("Fatal Error: JDK not installed. Go to java.sun.com and install.", error);
            return;
        }

        //Tries to compile. Success code is 0, so if something goes wrong, report.
        //println("begin record", error); //Starts recording compiling output
        int result = compiler.run(null, out, err, file.getAbsolutePath());
        //ArrayList<String> files = new ArrayList<>();
        //files.add(fileName);
        //boolean result = compiler.getTask(null, null, new ErrorReporter(), null, files, null).call();
        if (result != 0) {
            displayLog();
            //println("end record", error); //End recording and pull out the message
            println(lastErrorMessage, progErr);

            //println("Error type: " + result,error);
            println("DENIAL", warning);
            println("ANGER", warning);
            println("BARGAINING", warning);
            println("DEPRESSION", warning);
            println("NO ACCEPTANCE", warning);
            println("Failed to compile.", warning);
            return; //Return on error
        }

        //:D
        if(verboseCompiling) println("Code compiled with 0 errors.", progErr);

        if(verboseCompiling) println("Attempting to run code...", progErr);
        try {
            //Makes sure the JVM resets if it's already running.
            if(JVMrunning) 
                kill();

            //Some String constants for java path and OS-specific separators.
            String separator = System.getProperty("file.separator");
            String path = System.getProperty("java.home")
                + separator + "bin" + separator + "java";

            //Creates a new process that executes the source file.
            ProcessBuilder builder = null;
            if(runOptions.trim().equals("")) 
                builder = new ProcessBuilder(path, fileName);
            else 
                builder = new ProcessBuilder(path, runOptions, fileName);

            //Everything should be good now. Everything past this is on you. Don't mess it up.
            println("Build succeeded on " + java.util.Calendar.getInstance().getTime().toString(), progErr);
            println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~", progErr);                

            //Tries to run compiled code.
            JVM = builder.start();
            JVMrunning = true;

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
    private static void kill() {
        if (redirectErr != null) {
            redirectErr.close();
            redirectErr.interrupt();
            redirectErr = null;
        }

        if (redirectOut != null) {
            redirectOut.close();
            redirectOut.interrupt();
            redirectOut = null;
        }

        if (JVM != null) {
            JVM.destroy();
            JVM = null;
        }

        JVMrunning = false;

        println("JVM reset on " + java.util.Calendar.getInstance().getTime().toString(), progErr);
    }

    //Adds a new Thread that links a reader to a writer. Used to 
    private static IOHandlerThread redirectIOStream(Reader reader, TextOutputStream writer) {
        IOHandlerThread thr = new IOHandlerThread(reader, writer);
        thr.start();
        return thr;
    }

    //Used in compiling to mark out "dirty" areas of the code; ie, comments and string literals.
    //These dirty areas don't get their syntax highlighted or any special treatment.
    //Also used for syntax highlighting to update comments and string literal colors.
    //@returns: pairs of integers that represent dirty boundaries.
    private static ArrayList<Integer> getDirty(String code) {
        ArrayList<Integer> dirtyBounds = new ArrayList<>();

        //Handles string literals
        int j = -1;
        while(true) {
            j = code.indexOf("\"", j+1);
            if (j < 0) break;
            //Ignore escaped characters
            //if (!(j != 0 && code.substring(j-1, j).equals("\\"))) 
            if(j != 0) {
                if (!code.substring(j-1, j).equals("\\")) {
                    dirtyBounds.add(j);
                }
            } else {
                dirtyBounds.add(j);
            }
        }

        //End of line comments
        j = -1;
        while(true) {
            j = code.indexOf("//", j+1);
            if (j < 0 || isDirty(dirtyBounds, j)) break;
            dirtyBounds.add(j);
            //If there's no newline, then the comment lasts for the length of the code
            dirtyBounds.add(
                code.indexOf("\n", j+1) == -1 ? code.length() : code.indexOf("\n", j+1)); 
        }

        //Block comments (and javadoc comments)
        j = -1;
        while(true) {
            j = code.indexOf("/*", j+1);
            if (j < 0 || isDirty(dirtyBounds, j)) break;
            dirtyBounds.add(j);
            dirtyBounds.add(code.indexOf("*/", j+2) + 1); //Plus one to account for the slash, not the asterisk //j+two so "/*/" isn't parsed
        }

        return dirtyBounds;
    }

    //A helper method to check if a given position is dirty or not.
    private static boolean isDirty(ArrayList<Integer> dirty, int position) {
        if (dirty.size() % 2 == 0) 
            for (int k = 0; k < dirty.size(); k+=2) 
                if (position > dirty.get(k) && position < dirty.get(k+1)) 
                    return true;
        return false;
    }

    private static class FrameAction extends AbstractAction {
        public void actionPerformed(ActionEvent a) {
            //Note that this only works on *nix OSes.
            //For windows, get the first character of the action command, cast it to int, and compare that on a case-by-case basis.

            String command = a.getActionCommand();
            if (command.equals("e")) {
                //Log toggle.
                if (consoleDisplayed = !consoleDisplayed) {
                    //splitter.add(outputScroll);
                    //Show
                    splitter.setDividerLocation(defaultSliderPosition);
                } else {
                    //Hide
                    splitter.setDividerLocation(.999);
                }
            } else if (command.equals("r")) {
                //Gets the code into a string.
                String code = text.getText();

                //Marks certain areas as "dirty".
                //Dirty areas are places that shouldn't be considered for any keywords,
                //including "import", "extend", and so on.
                ArrayList<Integer> dirtyBounds = getDirty(code);

                if (text.getText().contains("class")) {
                    //This is a bit more explicit
                    //if(text.getText().trim().substring(0, text.getText().trim().indexOf("{")).contains("class")) {
                    //This means we should try to compile this as normal.

                    //Pulls out class name
                    int firstPos = code.indexOf("class");
                    while(isDirty(dirtyBounds, firstPos)) 
                        firstPos = code.indexOf("class", firstPos + 1);

                    int secondPos = code.indexOf("{"); //No checks here because who would possibly put a comment between "class" and "{"?
                    String name = code.substring(firstPos + "class".length() + 1, secondPos).trim();

                    //Just a safety check to make sure you don't try to modify this program while it's running.
                    if (name.equals("Playground")) {
                        System.out.println("I know what you're doing and I don't approve. I won't even compile that.");
                        return;
                        //println("Self-compiling. You were warned.", warning); //Allowed users to bootstrap this program. October 26, 2014
                    }

                    compileAndRun(name, code);
                } else {
                    //This means we should compile this as a playground.

                    //TODO: Try to assign every line to a variable, and print it out if it's by itself. IE, saying "int i = 0" won't do anything,
                    //but then just typing "i" or "i;" would print out "0". Add support for functions, too. So "factorial(5)" by itself would 
                    //print out "120" without any fluff.

                    //TODO: Lazy typing. "int i = 0" should be equivilant to "i = 0"

                    //TODO: Less need for casting. If "int.toString()" is called, modify in-place to "(new Integer(int)).toString()"
                    //Similar for calls that need Strings but are passed primitives; "promote" them by prepending ""+ to them.

                    //Common import statements built-in
                    String importDump = new String();
                    importDump+=("import java.util.*;\n" + 
                        "import javax.swing.*;\n" + 
                        "import javax.swing.event.*;\n" +
                        "import java.awt.*;\n" + 
                        "import java.awt.event.*;\n" + 
                        "import java.io.*;\n");

                    //User-defined or auto-generated methods
                    String methodDump = new String();

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

                        String sub = code.substring(pos, i);
                        if (shouldSkip || isDirty(dirtyBounds, pos) || 
                        sub.contains("while") || sub.contains("for") || sub.contains("new") || sub.contains("try") || sub.contains("catch")) 
                        {i=code.indexOf("(", i+1); continue;} //If this def. isn't a method or it's in a comment

                        int start = pos;
                        temp = 0; pos = code.indexOf("{", i+1);
                        int count = 1; shouldSkip = false;
                        if(pos != -1) {
                            if (code.indexOf(";", i+1) > pos || code.indexOf(";", i+1) == -1) {
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
                                //If there's a semicolon between the opening paranthesis and opening curly brace, this isn't a method!
                                i = code.indexOf("(", i+1);
                                continue;
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

                        //println("Found method: " + s);

                        //This makes using the method intuitive by effectively removing the need for static modifiers
                        if (!s.substring(0,s.indexOf("(")).contains("static")) {
                            s = "static " + s;
                            //println("Silently adding 'static' modifier", warning);
                        }

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
    }

    /**
     * This is the main writer for the log. You write to either "err" or "out" to write to the log.
     * Note that System.out and System.err are also linked to this, as well as println().
     */
    private static class TextOutputStream extends OutputStream {
        final JTextPane pane;
        final SimpleAttributeSet properties;
        final boolean isError;
        boolean recording;
        String out = "";

        public TextOutputStream(JTextPane p, SimpleAttributeSet s) {
            pane = p; properties=s; isError= s==null ? false : true; recording = false;
        }

        public void write(byte[] buffer, int offset, int length) {
            String text = new String(buffer, offset, length);
            //Java 8
            //SwingUtilities.invokeLater(()->{try{pane.getDocument().insertString(pane.getDocument().getLength(), text, properties);}catch(Exception e){}});

            if (!recording && text.equalsIgnoreCase("begin record")) {
                recording = true; return;
            }

            if (isError && recording) {
                out+=text; return;
            }

            if (recording && text.equalsIgnoreCase("end record")) {
                recording = false;
                //Do something with out here
                SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            try {pane.getDocument().insertString(pane.getDocument().getLength(), out, properties);}
                            catch(Exception e){}
                        }
                    });  
                lastErrorMessage = out;
                out = "";
                return;
            }

            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        try {pane.getDocument().insertString(pane.getDocument().getLength(), text, properties);}
                        catch(Exception e){}
                    }
                });
        }

        //For compatibility with writers when talking to the JVM.
        public void write(char[] buffer, int offset, int length) {
            String text = new String(buffer, offset, length);
            //Java 8
            //SwingUtilities.invokeLater(()->{try{pane.getDocument().insertString(pane.getDocument().getLength(), text, properties);}catch(Exception e){}});

            //Java 7
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        try{pane.getDocument().insertString(pane.getDocument().getLength(), text, properties);}
                        catch(Exception e){}
                    }
                });
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
        private String tempWrite = "";

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

                //Temporary holding string
                //String temp = "";

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

    private static class HighlightFilter extends DocumentFilter {
        //All keywords
        final String[] red = new String[]{"void","short","int","long","float","double","byte","boolean","char","class","interface","enum","extends","implements","assert",
                "import","package"};
        final String[] purple = new String[]{"public","private","protected","abstract","for","while","do","continue","break",
                "default","static","final","const","native","volatile","transient","synchronized","strictfp"}; 
        final String[] blue = new String[]{"if","else","try","catch","finally","goto","switch","case","return","throws","throw","this","new","instanceof","true","false",
                "null"};
        final String[] emerald = new String[]{"0","1","2","3","4","5","6","7","8","9"};

        //Colors
        SimpleAttributeSet r = new SimpleAttributeSet();
        SimpleAttributeSet p = new SimpleAttributeSet();
        SimpleAttributeSet b = new SimpleAttributeSet();
        SimpleAttributeSet e = new SimpleAttributeSet();
        SimpleAttributeSet g = new SimpleAttributeSet();
        SimpleAttributeSet none = new SimpleAttributeSet();

        //Used for auto-indenting.
        private boolean addTab = false;
        private int numTabs = 0;

        //Used to set up colors and such
        public HighlightFilter() {
            r.addAttribute(StyleConstants.Foreground, Color.RED);
            p.addAttribute(StyleConstants.Foreground, new Color(70,0,120)); //new Color(123,47,138)
            b.addAttribute(StyleConstants.Foreground, Color.BLUE);
            e.addAttribute(StyleConstants.Foreground, new Color(0,160,0));
            g.addAttribute(StyleConstants.Foreground, new Color(0,200,0)); //Light gray?
            none.addAttribute(StyleConstants.Foreground, Color.BLACK);
        }

        public void insertString(FilterBypass fb, int offset, String s, AttributeSet set) {
            System.out.println("Hey!");
        }

        public void replace(FilterBypass fb, int offset, int length, String s, AttributeSet set) {
            try {
                //This makes the default tab much less extreme.
                if(s.equals("\t")) s="        "; //Only eight spaces

                //This auto-indents new lines for you. Beta.
                //if(s.equals("{") || s.equals("(")) { addTab = true; numTabs++; }
                //if(s.equals("}") || s.equals("(")) { if (addTab) s+="\b\b\b\b\b\b\b\b"; numTabs--; };
                //if(s.equals("\n")) { for(int i=0;i<numTabs;i++) s+="        "; }

                /*
                 * Keep spacing same as last
                 * { + anything -> enter = tab + 1
                 * if }, undo a tab
                 */

                fb.replace(offset, length, s, set);
                String all = fb.getDocument().getText(0, fb.getDocument().getLength());
                int l = huntLeft(all,offset), r = huntRight(all,offset+s.length());

                dirtyBounds = getDirty(all);

                //Todo: Optimize the dirtybound selector by using "isdirty" on input or similar.
                //if(isDirty(dirtyBounds, offset)) {
                //    println("Dirty! Updating boundaries.",progErr);
                //}

                //Optimization for large inputs- does syntax highlighting on a separate thread if needed.
                if (s.length() > 10000) 
                    new Thread() {public void run() {removeHighlights(fb,l,r); highlightArea(fb,all,l,r);}}.start();
                else {
                    removeHighlights(fb,l,r); 
                    highlightArea(fb,all,l,r);
                }
            } catch (BadLocationException b) {
                b.printStackTrace();
            }         
        }

        public void remove(FilterBypass fb, int offset, int length) {
            try {
                //Removes highlights from the dirty area first
                String all = fb.getDocument().getText(0, fb.getDocument().getLength());
                removeHighlights(fb,huntLeft(all,offset),huntRight(all,offset+length)); 

                //Removes the given text
                fb.remove(offset, length);

                //Updates highlights on the new dirty area
                all = fb.getDocument().getText(0, fb.getDocument().getLength()); 
                dirtyBounds = getDirty(all); //Big note: Optimise the dirty bound selector by only making it redraw where it's dirty, instead of the entire document. \
                highlightArea(fb, all, huntLeft(all,offset), huntRight(all,offset+length)); //Minus one because we removed one
            } catch (BadLocationException b) {
                b.printStackTrace();
            }
        }

        //Finds the next whitespace character in the string 'in' to the left of the given offset.
        private int huntLeft(String in, int offset) {
            char temp = 0;
            for(int i = offset - 1; i >= 0; i--) {
                temp = in.charAt(i);
                if(temp == '\n' || temp == '\r')
                    return i;
            }
            return 0; //eof counts
        }

        //Finds the next whitespace character in the string 'in' to the right of the given offset.
        private int huntRight(String in, int offset) {
            char temp = 0;
            for(int i = offset + 1; i < in.length(); i++) {
                temp = in.charAt(i);
                if(temp == '\n' || temp == '\r')
                    return i;
            }
            return in.length(); //eof counts. Note this is the length because we increased it by one!
        }

        //Highlights the given string from positions left to right
        private void highlightArea(FilterBypass fb, String s, int left, int right) {
            String sub = s.substring(left, right);
            //removeHighlights(fb, s, left, right);
            //String whitespace = new String(" \n\r");
            //println("String to update: '" + s.substring(left, right) + "'");

            //Todo: make the code check for whitespace OR eof on either side of the string before highlighting it
            //Further optimisation for large inputs: combine all loops into one, and have an if statement to determine color.
            //  ie, if there are 17 keywords that should be red, then check if the index found is <17, and if so, color red.

            for(String st : red) {
                int i = -1;
                while ((i = sub.indexOf(st,i+1)) != -1) {
                    ((StyledDocument)fb.getDocument()).setCharacterAttributes(i+left, st.length(), r, true);
                }
            }

            for(String st: purple) {
                int i = -1;
                while ((i = sub.indexOf(st,i+1)) != -1) {
                    ((StyledDocument)fb.getDocument()).setCharacterAttributes(i+left, st.length(), p, true);
                }
            }

            for(String st: blue) {
                int i = -1;
                while ((i = sub.indexOf(st,i+1)) != -1) {
                    ((StyledDocument)fb.getDocument()).setCharacterAttributes(i+left, st.length(), b, true);
                }
            }

            for(String st: emerald) {
                int i = -1;
                while ((i = sub.indexOf(st,i+1)) != -1) {
                    ((StyledDocument)fb.getDocument()).setCharacterAttributes(i+left, st.length(), e, true);
                }
            }

            //Handles comments and string literals.
            //if (dirtyBounds.size() % 2 == 0) 
            try {
                for(int i = 0; i < dirtyBounds.size()-1; i+=2) {
                    ((StyledDocument)fb.getDocument()).setCharacterAttributes(dirtyBounds.get(i), dirtyBounds.get(i+1)-dirtyBounds.get(i)+1, g, true);
                }
            } catch (ArrayIndexOutOfBoundsException a) {}
        }

        private void removeHighlights(FilterBypass fb, int left, int right) {
            //println("String to remove from: '" + s.substring(left, right) + "'");
            //println("Removing from " + left + " to " + right);
            ((StyledDocument)fb.getDocument()).setCharacterAttributes(left, (right-left), none, true);
        }
    }

    private static class OptionFrame extends JFrame {
        static JTabbedPane options;
        static JPanel op1;
        static JPanel op2;
        static JTextField run, reset, opt, terminal, help;

        public OptionFrame(JFrame f) {
            super("Options");
            setSize(320, 240);
            setResizable(false);
            options = new JTabbedPane();
            op1 = new JPanel();
            op1.setLayout(new GridLayout(6,2));

            op1.add(new JLabel("Terminal orientation?"));
            String s = isVerticalSplitterPane ? "Vertical" : "Horizontal";
            JCheckBox vertical = new JCheckBox(s, isVerticalSplitterPane);
            vertical.addItemListener(new ItemListener(){
                    public void itemStateChanged(ItemEvent i){
                        isVerticalSplitterPane = vertical.isSelected(); 
                        vertical.setText(isVerticalSplitterPane ? "Vertical" : "Horizontal");
                        updateSplitterOrientation();
                        splitter.setDividerLocation(defaultSliderPosition);
                        saveSettings();
                    }
                });
            op1.add(vertical);  

            op1.add(new JLabel("Default slider pos?"));
            JSlider slider = new JSlider(0, 100, (int)(defaultSliderPosition*100));
            slider.addChangeListener(new ChangeListener(){
                    public void stateChanged(ChangeEvent c) {
                        defaultSliderPosition = slider.getValue() / 100.0;
                        splitter.setDividerLocation(defaultSliderPosition);
                        saveSettings();
                    }
                });
            op1.add(slider);

            op1.add(new JLabel("Verbose terminal?"));
            JCheckBox verbose = new JCheckBox(verboseCompiling?"Enabled":"Disabled", verboseCompiling);
            verbose.addItemListener(new ItemListener(){
                    public void itemStateChanged(ItemEvent i){
                        verboseCompiling = verbose.isSelected(); 
                        verbose.setText(verboseCompiling ? "Enabled" : "Disabled");
                        saveSettings();
                    }
                });
            op1.add(verbose);             

            op1.add(new JLabel("Enable warnings?"));
            JCheckBox warnings = new JCheckBox(warningsEnabled ? "Enabled" : "Disabled", warningsEnabled);
            warnings.addItemListener(new ItemListener(){
                    public void itemStateChanged(ItemEvent i){
                        warningsEnabled = warnings.isSelected(); 
                        warnings.setText(warningsEnabled ? "Enabled" : "Disabled");
                        saveSettings();
                    }
                });
            op1.add(warnings);   

            op1.add(new JLabel("Compiler options:"));
            JTextField compile = new JTextField(compileOptions);
            compile.getDocument().addDocumentListener(new DocumentListener() {
                    public void changedUpdate(DocumentEvent d) {update();}

                    public void removeUpdate(DocumentEvent d)  {update();}

                    public void insertUpdate(DocumentEvent d)  {update();}

                    public void update() {
                        compileOptions = compile.getText();
                        saveSettings();
                    }
                });
            op1.add(compile);

            op1.add(new JLabel("Run options:"));
            JTextField run = new JTextField(runOptions);
            run.getDocument().addDocumentListener(new DocumentListener() {
                    public void changedUpdate(DocumentEvent d) {update();}

                    public void removeUpdate(DocumentEvent d)  {update();}

                    public void insertUpdate(DocumentEvent d)  {update();}

                    public void update() {
                        runOptions = run.getText();
                        saveSettings();
                    }
                });
            op1.add(run);            

            options.addTab("Preferences", op1);
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
            message.setText("Default keybindings:\n     -Cmd+e to toggle terminal\n     -Cmd+r to run\n     -Cmd+k to reset JVM\n" +
                "     -Cmd+/ to show help\n     -Cmd+o for options\n\n" + 
                "Special notes: \n     -Import statements can be typed anywhere \n     -Type methods anywhere and they'll work\n\n" +
                "Other: \n     -You can also use this for normal Java editing!");

            add(message, "Center");
            setVisible(true);
        }
    }

    private static class ErrorReporter implements DiagnosticListener {
        public void report(Diagnostic d) {
            println(d.getMessage(null), progErr);
        }
    }
}