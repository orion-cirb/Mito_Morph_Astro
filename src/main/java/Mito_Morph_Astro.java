/*
 * Analyze mitochondrial morphological network in astrocyte
 * 
 * Author Philippe Mailly
 */


/* 
* Images on local machine
*/


import Mito_Utils.Mito_Processing;

import ij.*;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageHandler;

import ij.gui.Roi;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import java.util.ArrayList;
import java.util.Collections;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import java.awt.Rectangle;
import java.io.FilenameFilter;
import loci.common.Region;
import org.apache.commons.io.FilenameUtils;


public class Mito_Morph_Astro implements PlugIn {

    private String imageDir = "";
    public static String outDirResults = "";
    private File inDir;
    public BufferedWriter outPutGlobalResults;    
    
    Mito_Processing proc = new Mito_Processing();

    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        String imageExt = "czi";
        final boolean canceled = false;
        
        try {
            if (canceled) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            if (!proc.checkInstalledModules()) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            imageDir = IJ.getDirectory("Images folder");
            if (imageDir == null) {
                return;
            }
            inDir = new File(imageDir);
            ArrayList<String> imageFiles = proc.findImages(imageDir, "czi");
            if (imageFiles == null) {
                return;
            }
            // create output folder
            outDirResults = imageDir + "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }           
    
            // Reset foreground and background
            IJ.run("Colors...", "foreground=white background=black");
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            Collections.sort(imageFiles);

            /*
            * Write headers results for results file
            */
            // Global file for mito results
            String resultsName = "Results.xls";
            String header = "ImageName\tRoi\tRoi volume\tMito number\tMito volume\tMito branch number\tMito branch length\t"
                    + "Mito end points\tMito junction number\n";
            outPutGlobalResults = proc.writeHeaders(outDirResults+resultsName, header); 
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                reader.setId(f);
                reader.setSeries(0);
                ImporterOptions options = new ImporterOptions();
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setCrop(true);
                // find rois if exist roi file
                String roiFile = "";
                if (new File(inDir + File.separator+rootName + ".zip").exists())
                    roiFile = inDir + File.separator+rootName + ".zip";
                else {
                    IJ.showStatus("No roi file found skip image !!!");
                    return;
                }
                ArrayList<Roi> rois = new ArrayList<>();
                // find rois
                System.out.println("Find roi " + new File(roiFile).getName());
                RoiManager rm = new RoiManager(false);
                rm.runCommand("Open", roiFile);
                // Store roi by type polygon / point
                ArrayList<Roi> roiPts = proc.findRoi(rm, Roi.POINT);
                ArrayList<Roi> roiPolys = proc.findRoi(rm, Roi.FREELINE);
                for (Roi roiPoly : roiPolys) {
                    String roiName = roiPoly.getName();
                    Roi roiPt = null;
                    for (Roi r : roiPts)
                        if (r.getName().contains(roiName))
                            roiPt = r;
                    if (roiPt == null) {
                        IJ.showStatus("No point roi file found skip image !!!");
                        return;
                    }
                    Rectangle rect = roiPoly.getBounds();
                    Region reg = new Region(rect.x, rect.y, rect.width, rect.height);
                    options.setCropRegion(0, reg);
                    
                     // Mito channel 2
                    options.setCBegin(0, 1);
                    options.setCEnd(0, 1);
                    System.out.println("Opening mito channel");
                    ImagePlus imgMitoOrg = BF.openImagePlus(options)[0];
                    // Find Mitos
                    Objects3DPopulation mitoPop = proc.find_Mito(imgMitoOrg, roiPoly);
                    System.out.println("Mito pop = "+ mitoPop.getNbObjects());
                    
                    // Find mito network morphology
                    // Skeletonize
                    double[] skeletonParams = proc.analyzeSkeleton(imgMitoOrg, roiPoly, mitoPop, outDirResults+rootName);
                    // Compute global parameters                        
                    IJ.showStatus("Writing parameters ...");
                    proc.computeParameters(mitoPop, imgMitoOrg, skeletonParams, roiPoly, roiPt, rootName, outPutGlobalResults);
                   
                    // Save objects image
                    ImageHandler imhMitoObjects = ImageHandler.wrap(imgMitoOrg).createSameDimensions();
                    mitoPop.draw(imhMitoObjects);
                    FileSaver ImgObjectsFile = new FileSaver(imhMitoObjects.getImagePlus());
                    ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Roi-"+roiName+"_Objects.tif");
                    proc.flush_close(imhMitoObjects.getImagePlus());
                    proc.flush_close(imgMitoOrg);
                }

            }
            outPutGlobalResults.close();
            IJ.showStatus("Process done");
        }   catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(Mito_Morph_Astro.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
}
