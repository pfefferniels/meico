package meico.app.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import nu.xom.ParsingException;
import nu.xom.xslt.XSLException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * This part of the interface will be the place where all the data is shown, dropped, converted, ...
 * @author Axel Berndt
 */
public class Workspace extends ScrollPane {
    private MeicoApp app;                                         // this is a link to the parent application
    private Pane container;                                       // this is the "drawing area", all contents go in here, Pane features a better behavior than Group in this context (resizes only in positive direction, Pane local bounds start always at (0, 0) in top left whereas Group's bounds start at the top-leftmost child at (0+x, 0+y)).
    private ArrayList<DataObject> data = new ArrayList<>();         // this holds the data that is presented in the workspace area
    private ArrayList<DataObject> soundbanks = new ArrayList<>();   // the list of soundbanks that are present in the workspace (they are also contained in this.data)
    private ArrayList<DataObject> xslts = new ArrayList<>();        // the list of XSLTs that are present in the workspace (they are also contained in this.data)

    public Workspace(MeicoApp app) {
        super();
        this.app = app;                                         // link to the parent app

        // layout
        this.setStyle(Settings.WORKSPACE);                      // this is strange but necessary to set the "whole" background transparent
        VBox.setVgrow(this, Priority.ALWAYS);                   // audomatically maximize the height of this ScrollPane
//        this.setMaxWidth(Double.MAX_VALUE);                     // also maximize its width (this happens automatically within a VBox)
        this.setPannable(true);                                 // enable panning in this ScrollPane

        this.container = new Pane();                            // create the "drawing area"

        this.setFileDrop();                                     // set file drop functionality on the workspace
        this.setContent(this.container);                        // set it as the content of the scrollpane
    }

    /**
     * get the container that holds all contents of the workspace
     * @return
     */
    protected synchronized Pane getContainer() {
        return this.container;
    }

    /**
     * access the app
     * @return
     */
    protected synchronized MeicoApp getApp() {
        return this.app;
    }

    /**
     * remove all data objects from the workspace (more precisely from the container)
     */
    protected synchronized void clearAll() {
        for (DataObject object : this.data)
            object.clear();
        this.container.getChildren().clear();
        this.data.clear();
        this.soundbanks.clear();
        this.xslts.clear();
        this.app.getPlayer().setSoundbank(null);
//        System.gc();
    }

    /**
     * removes the specified object from the workspace
     * @param object
     */
    protected synchronized void remove(DataObject object) {
        this.data.remove(object);

        if (object.getDataType() == meico.app.gui.Soundbank.class)
            this.soundbanks.remove(object);
        else if (object.getDataType() == meico.app.gui.XSLTransform.class)
            this.xslts.remove(object);

        this.container.getChildren().remove(object);
        object.clear();
//        System.gc();
    }

    /**
     * create a data object from a given file
     * @param data
     * @return
     * @throws InvalidMidiDataException
     * @throws ParsingException
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    private synchronized DataObject makeDataObject(Object data) throws InvalidMidiDataException, ParsingException, IOException, UnsupportedAudioFileException, XSLException {
        return new DataObject(data, this);
    }

    /**
     * add a data object to the workspace
     * @param dataObject
     * @param x
     * @param y
     */
    protected synchronized void addDataObjectAt(DataObject dataObject, double x, double y) {
        dataObject.setLayout(x, y);
        this.container.getChildren().add(dataObject);
        this.data.add(dataObject);

        if (dataObject.getDataType() == meico.app.gui.Soundbank.class)            // if it is a soundbank
            this.addToSoundbanks(dataObject);                       // add it also to the soundbanks
        else  if (dataObject.getDataType() == XSLTransform.class)   // if it is an xslt
            this.addToXSLTs(dataObject);                            // add it also to the xslts
    }

    /**
     * draws a line between two data objects
     * @param parent
     * @param child
     */
    protected synchronized DataObjectLine addDataObjectLine(DataObject parent, DataObject child) {
        DataObjectLine line = new DataObjectLine(parent, child);
        this.container.getChildren().add(0, line);                  // add it behind all other elements so that it does not get in the way with mouse interaction
        return line;
    }

    /**
     * this adds the given DataObject to the soundbanks list
     * @param soundbank
     */
    protected synchronized void addToSoundbanks(DataObject soundbank) {
        this.soundbanks.add(soundbank);
    }

    /**
     * This deactivates all soundbanks in the workspace, but only visually.
     * It does not force the midiplayer to load a default soundbank so the one
     * that is currently loaded will still be used for MIDI synthesis.
     * But now another soundbank can be loaded and set active without worrying about the others.
     */
    protected synchronized void silentDeactivationOfAllSoundbanks() {
        for (DataObject o : this.soundbanks) {
            ((meico.app.gui.Soundbank)o.getData()).silentDeactivation();
        }
    }

    /**
     * returns the soundbank that is currently used for MIDI synthesis so that it may also be used for MIDI to audio rendering
     * @return
     */
    protected File getActiveSoundbank() {
        for (DataObject o : this.soundbanks) {
            meico.app.gui.Soundbank s = (meico.app.gui.Soundbank)o.getData();
            if (s.isActive())
                return s.getFile();
        }
        return Settings.soundbank;
    }

    /**
     * this adds the given DataObject to the xslts list
     * @param xslt
     */
    protected synchronized void addToXSLTs(DataObject xslt) {
        this.xslts.add(xslt);
    }

    /**
     * returns the XSLTransform that is currently activated
     * @return
     */
    protected File getActiveXSLT() {
        for (DataObject o : this.xslts) {
            meico.app.gui.XSLTransform x = (meico.app.gui.XSLTransform)o.getData();
            if (x.isActive())
                return x.getFile();
        }
        return null;
    }

    /**
     * This deactivates all xsl transforms.
     */
    protected synchronized void deactivateAllXSLTs() {
        for (DataObject o : this.xslts) {
            ((meico.app.gui.XSLTransform)o.getData()).deactivate();
        }
    }

    /**
     * This sets file drop frunctionality to a Region object.
     */
    private synchronized void setFileDrop() {
        this.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        // Dropping over surface
        this.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                success = true;
                String filePath = null;
                for (File file : dragboard.getFiles()) {
                    filePath = file.getAbsolutePath();
                    this.app.getStatuspanel().setMessage("File drop " + filePath);
                    Point2D local = this.container.sceneToLocal(event.getSceneX(), event.getSceneY());// get local drop coordinates in container
                    try {
                        DataObject data = this.makeDataObject(file);
                        this.addDataObjectAt(data, local.getX(), local.getY());
                    } catch (ParsingException | InvalidMidiDataException | IOException | UnsupportedAudioFileException | XSLException e) {
                        this.app.getStatuspanel().setMessage(e.toString());
                        e.printStackTrace();
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }
}
