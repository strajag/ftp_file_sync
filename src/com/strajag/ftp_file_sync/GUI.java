package com.strajag.ftp_file_sync;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

public class GUI
{
    private FtpFileSync ftpFileSync;

    private final JFrame frame = new JFrame();
    private final JPanel panel = new JPanel();

    private final JButton downloadButton = new JButton("download");
    private final JButton uploadButton = new JButton("upload");
    private final JButton settingButton = new JButton("setting");
    private final JButton exitButton = new JButton("exit");

    GUI()
    {
        setPanel();
        setFrame();

        try
        {
            ftpFileSync = new FtpFileSync();
        }
        catch(Exception exception)
        {
            throwException(exception);
        }
    }

    void setFrame()
    {
        Image image = null;

        try
        {
            image = ImageIO.read(new File("./data/ftp_file_sync_icon.png"));
        }
        catch(Exception exception)
        {
            exception.printStackTrace();
        }

        frame.setTitle("fpt_file_sync");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setPreferredSize(new Dimension(300, 150));
        frame.setResizable(false);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setIconImage(image);
        frame.setVisible(true);
    }

    void setPanel()
    {
        panel.setBackground(Color.BLACK);
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        uploadButton.setPreferredSize(downloadButton.getPreferredSize());
        downloadButton.setPreferredSize(downloadButton.getPreferredSize());
        settingButton.setPreferredSize(downloadButton.getPreferredSize());
        exitButton.setPreferredSize(downloadButton.getPreferredSize());

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        panel.add(downloadButton, gbc);
        gbc.gridx = 1;
        panel.add(uploadButton, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 0, 10, 0);
        panel.add(settingButton, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(exitButton, gbc);

        ActionListener actionListener = event ->
        {
            setButtonsEnabled(false);

            try
            {
                JButton pressedButton = (JButton)event.getSource();

                if(pressedButton.equals(downloadButton))
                {
                    JDialog jDialog = new JDialog(frame);
                    setLoadingDialog(jDialog, new SwingWorker<String, Void>()
                    {
                        @Override
                        protected String doInBackground() throws Exception
                        {
                            ftpFileSync.download();
                            return "?";
                        }
                        @Override
                        protected void done()
                        {
                            jDialog.dispose();
                        }
                    });
                    JOptionPane.showMessageDialog(frame, "files successfully downloaded and synced", "download", JOptionPane.INFORMATION_MESSAGE);
                }
                else if(pressedButton.equals(uploadButton))
                {
                    JDialog jDialog = new JDialog(frame);
                    setLoadingDialog(jDialog, new SwingWorker<String, Void>()
                    {
                        @Override
                        protected String doInBackground() throws Exception
                        {
                            ftpFileSync.upload();
                            return "?";
                        }
                        @Override
                        protected void done()
                        {
                            jDialog.dispose();
                        }
                    });
                    JOptionPane.showMessageDialog(frame, "files successfully uploaded", "upload", JOptionPane.INFORMATION_MESSAGE);
                }
                else if(pressedButton.equals(settingButton))
                {
                    if(System.getenv("OS") != null && System.getenv("OS").contains("Windows"))
                    {
                        if(Desktop.isDesktopSupported())
                            Desktop.getDesktop().edit(ftpFileSync.settingsFile);
                        else
                            Runtime.getRuntime().exec("notepad " + ftpFileSync.settingsFile);
                    }
                    else
                        Runtime.getRuntime().exec("xdg-open " + ftpFileSync.settingsFile);
                }
                else
                    System.exit(0);
            }
            catch(Exception exception)
            {
                throwException(exception);
            }

            setButtonsEnabled(true);
        };

        downloadButton.addActionListener(actionListener);
        uploadButton.addActionListener(actionListener);
        settingButton.addActionListener(actionListener);
        exitButton.addActionListener(actionListener);
    }

    void setButtonsEnabled(boolean isEnabled)
    {
        downloadButton.setEnabled(isEnabled);
        uploadButton.setEnabled(isEnabled);
        settingButton.setEnabled(isEnabled);
        exitButton.setEnabled(isEnabled);
    }

    void setLoadingDialog(JDialog jDialog, SwingWorker<String, Void> worker) throws Exception
    {
        JLabel jLabel = new JLabel("please wait... (this may take a while)");
        jLabel.setFont(new Font(jLabel.getFont().getName(), Font.PLAIN, 20));

        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.add(jLabel, BorderLayout.CENTER);
        jDialog.setUndecorated(true);
        jDialog.getContentPane().add(jPanel);
        jDialog.pack();
        jDialog.setLocationRelativeTo(frame);
        jDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        jDialog.setModal(true);

        worker.execute();
        jDialog.setVisible(true);
        worker.get();
    }

    void throwException(Exception exception)
    {
        PrintWriter pw = new PrintWriter(new StringWriter());
        exception.printStackTrace(pw);
        JOptionPane.showMessageDialog(frame, exception.getMessage(), "error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }
}


