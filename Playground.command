cd "$(dirname "$0")"
javac Playground.java
java Playground
cd "$(dirname "$0")"
rm Playground.ctxt
rm Playground.class
rm Playground\$1.class
rm Playground\$2.class
rm Playground\$3.class
rm Playground\$FrameAction.class
rm Playground\$HelpFrame.class
rm Playground\$IOHandlerThread.class
rm Playground\$HighlightFilter.class
rm Playground\$OptionFrame.class
rm Playground\$Reloader.class
rm Playground\$TextOutputStream.class
killall Terminal