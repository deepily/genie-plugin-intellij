package ai.deepily.genie.plugins.pycharm;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.Runtime;
import java.lang.Process;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InsertVoiceTranscriptionAction extends AnAction {

    @Override
    public void actionPerformed( @NotNull AnActionEvent event ) {

        Project project = event.getRequiredData( CommonDataKeys.PROJECT );
        Editor editor = event.getRequiredData( CommonDataKeys.EDITOR );
        Document document = editor.getDocument();

        String default_mode = ActionManager.getInstance().getId( this );
        String menuText = event.getPresentation().getText();
        // System.out.println( "########################################################################################" );
        System.out.println( MessageFormat.format( "# Menu item [{0}] has ID [{1}]", menuText, default_mode ) );
        // System.out.println( "########################################################################################" );

//        if ( menuText.equals( "Insert Voice to Python" ) ) {
//            // System.out.println( "'Insert Voice to Python' called" );
//            default_mode = "transcribe_and_clean_python";
//        } else if ( menuText.equals( "Explain This Code or Error (Clipboard)" ) ) {
//            System.out.println( "Explain This called (Clipboard)" );
//            default_mode = "ai_explain_code_from_clipboard";
//        } else {
//            System.out.println( "'Insert Voice to Prose' called (default)" );
//            default_mode = "transcribe_and_clean_prose";
//        }

        // Run this code in another thread so that IDE doesn't complain about The dreaded "stop the world" blocking.
        String finalDefault_mode = ActionManager.getInstance().getId( this );
        new Thread( () -> {

            String textFromClipboard = "¡ERROR!";
            try {
                String command = "/Volumes/projects/genie-in-the-box/run-genie-gui.sh record_once_on_startup=True default_mode=" + finalDefault_mode;

                event.getPresentation().setEnabled( Boolean.FALSE );
                Process process = Runtime.getRuntime().exec( command );
                int exitCode = process.waitFor();
                System.out.println( "Exit code: " + exitCode );
                event.getPresentation().setEnabled( Boolean.TRUE );

                textFromClipboard = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData( DataFlavor.stringFlavor );

            } catch ( Exception e) {
                throw new RuntimeException( e );
            }
            // Check for selected text Within read action
            AtomicBoolean textSelected = new AtomicBoolean(false );
            AtomicInteger start = new AtomicInteger();
            AtomicInteger stop = new AtomicInteger();
            ApplicationManager.getApplication().runReadAction( () -> {
                textSelected.set( editor.getCaretModel().getCurrentCaret().hasSelection() );
                if ( textSelected.get() ) {
                    Caret caret = editor.getCaretModel().getCurrentCaret();
                    start.set(caret.getSelectionStart());
                    stop.set(caret.getSelectionEnd());
                }
            } );
            if ( textSelected.get() ) {
                WriteCommandAction.runWriteCommandAction(project, () -> document.replaceString( start.get(), stop.get(), "" ) );
            }

            // Paste text from clipboard
            Caret caret = editor.getCaretModel().getCurrentCaret();
            String finalTextToPaste = textFromClipboard;
            WriteCommandAction.runWriteCommandAction( project, () -> document.insertString( caret.getOffset(), finalTextToPaste ) );

            // Move the caret to the end of the pasted text string
            AtomicInteger offset = new AtomicInteger();
            ApplicationManager.getApplication().runReadAction( () -> {
                offset.set( editor.getCaretModel().getCurrentCaret().getOffset() );
            } );
            int newOffset = offset.get() + finalTextToPaste.length();
            WriteCommandAction.runWriteCommandAction( project, () -> caret.moveToOffset( newOffset ) );

        }).start();
    }
    private String getRestText( String urlString ) {

        try {

            // Providing the website URL
            URL url = new URL( urlString );

            // Creating an HTTP connection
            HttpURLConnection MyConn = (HttpURLConnection) url.openConnection();

            // Set the request method to "GET"
            MyConn.setRequestMethod("GET");

            // Collect the response code
            int responseCode = MyConn.getResponseCode();
            System.out.println("GET Response Code :: " + responseCode);

            if ( responseCode == MyConn.HTTP_OK ) {

                // Create a reader with the input stream reader.
                BufferedReader in = new BufferedReader(new InputStreamReader(MyConn.getInputStream()));
                String inputLine;

                // Create a string buffer
                StringBuffer response = new StringBuffer();

                // Write each of the input line
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();

            } else {

                String errorMsg = String.format( "Server error code [{0}]", responseCode );
                System.out.println( errorMsg );

                return errorMsg;
            }
        } catch(  Exception e ) {

            String errorMsg = String.format( "General exception caught [{0}]", e.toString() );
            System.out.println( errorMsg );
            e.printStackTrace();

            return errorMsg;
        }
    }
}
