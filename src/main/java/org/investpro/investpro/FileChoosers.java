package org.investpro.investpro;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class FileChoosers {
    File file1=new File("media/mediaview.mp4");

    public FileChoosers()  {
    }

    public void start() throws Exception {
        // TODO Auto-generated method stub
        FileChooser file = new FileChooser();
        file.setTitle("Open file");
        Stage primaryStage = new Stage();

        file1 = file.showOpenDialog(primaryStage);



    }



        public File selectFile () {
            System.out.println("Filename "+file1);
            return file1;

        }

}