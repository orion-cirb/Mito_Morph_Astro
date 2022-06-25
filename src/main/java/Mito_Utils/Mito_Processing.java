package Mito_Utils;


import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.SkeletonResult;


 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose dots_Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author phm
 */
public class Mito_Processing {
    

    // Mito filter size
    private final double minMito = 10;
    private final double maxMito = Double.MAX_VALUE;
    
    public Calibration cal = new Calibration(); 
    public CLIJ2 clij2 = CLIJ2.getInstance();

    
    
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    
    
    /**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        return(images);
    }
       
    
    
    /**
     * Find image calibration
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta, ImageProcessorReader reader) {
        cal = new Calibration();  
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        return(cal);
    }
    
    public Calibration getCalib()
    {
        System.out.println("x cal = " +cal.pixelWidth+", z cal=" + cal.pixelDepth);
        return cal;
    }
    
    
    /**
    * 
    * @param FileResults
    * @param resultsFileName
    * @param header
    * @return 
    */
    public BufferedWriter writeHeaders(String outFileResults, String header) throws IOException {
       FileWriter FileResults = new FileWriter(outFileResults, false);
       BufferedWriter outPutResults = new BufferedWriter(FileResults); 
       outPutResults.write(header);
       outPutResults.flush();
       return(outPutResults);
    } 
    
    
    /**
     * Find roi by type
     */
    public ArrayList<Roi> findRoi(RoiManager rm, int type) {
        ArrayList<Roi> rois = new ArrayList<>();
        for (Roi roi : rm.getRoisAsArray()) {
            if (type != Roi.POINT) {
                if (roi.getType() != Roi.POINT)
                   rois.add(roi); 
            }
            else
                if (roi.getType() == type)
                rois.add(roi);
        }
    return (rois);
    }
        
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param size1
     * @param size2
     * @return imgGauss
     */ 
    public ClearCLBuffer DOG(ClearCLBuffer imgCL, double size1, double size2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
    /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        return(imgCLBin);
    }
    
    /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.median3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
    /**
     * Mito segmentation
     * @param img
     * @return 
     */
    public Objects3DPopulation find_Mito(ImagePlus img, Roi roi) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgMed = median_filter(imgCL, 2, 2);
        clij2.release(imgCL);
        ClearCLBuffer imgDOG = DOG(imgMed, 2, 3);
        clij2.release(imgMed);
        ClearCLBuffer imgCLBin = threshold(imgDOG, "Triangle");
        clij2.release(imgDOG);
        ClearCLBuffer imgLabelled = clij2.create(imgDOG);
        clij2.connectedComponentsLabelingBox(imgCLBin, imgLabelled);
        clij2.release(imgCLBin);
         ClearCLBuffer labelsSizeFilter = clij2.create(imgLabelled);
        // filter size
        clij2.excludeLabelsOutsideSizeRange(imgLabelled, labelsSizeFilter, minMito,
                maxMito);
        ImagePlus imgBin = clij2.pull(labelsSizeFilter);
        clij2.release(imgLabelled);
        clij2.release(labelsSizeFilter);
        clearOutSide(imgBin, roi);
        Objects3DPopulation mitoPop = new Objects3DPopulation(ImageHandler.wrap(imgBin));
        return(mitoPop);
    } 
        
    
    public Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        labels.setCalibration(cal);
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }
    

 
    public ImagePlus doZProjection(ImagePlus img) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(ZProjector.MAX_METHOD);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
    
// Flush and close images
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    } 
    
    
    /**
     * Clear out side roi
     * @param img
     * @param roi
     */
    public void clearOutSide(ImagePlus img, Roi roi) {
        roi.setLocation(0, 0);
        for (int n = 1; n <= img.getNSlices(); n++) {
            ImageProcessor ip = img.getImageStack().getProcessor(n);
            ip.setRoi(roi);
            ip.setBackgroundValue(0);
            ip.setColor(0);
            ip.fillOutside(roi);
        }
        img.deleteRoi();
        img.updateAndDraw();
    }
    
    
    /**
     * Analayze skeleton
     * @param img
     * @param output
     * @return {#branch, branchLenght, #endPoint, #junction}
     */

    public double[] analyzeSkeleton (ImagePlus img, Roi roiPt, Objects3DPopulation pop, String outDir, String outFileName) throws IOException {
        ImageHandler imh = ImageHandler.wrap(img).createSameDimensions();
        pop.draw(imh, 255);
        ImagePlus imgBin = imh.getImagePlus();
        imh.closeImagePlus();
        imgBin.setCalibration(cal);
        IJ.run(imgBin,"8-bit","");
        IJ.run(imgBin, "Skeletonize (2D/3D)", "");
	String imgTitle = img.getTitle();
        AnalyzeSkeleton_ analyzeSkeleton = new AnalyzeSkeleton_();
        AnalyzeSkeleton_.calculateShortestPath = true;
        analyzeSkeleton.setup("",imgBin);
        SkeletonResult skeletonResults = analyzeSkeleton.run(AnalyzeSkeleton_.NONE, false, true, null, true, false);
        ImageStack imgStackLab = analyzeSkeleton.getLabeledSkeletons();
        //  compute parameters for each skeleton
        IJ.showStatus("Computing parameters for each skeleton ...");
        ImagePlus imgLab = new ImagePlus(imgTitle+"_LabelledSkel.tif", imgStackLab);
        ImagePlus imgLabProj = doZProjection(imgLab);
        IJ.run(imgLabProj, "3-3-2 RGB", "");
        imgLabProj.setCalibration(img.getCalibration());
        flush_close(imgLab);
        int[] branchNumbers = skeletonResults.getBranches();
        double[] branchLengths = skeletonResults.getAverageBranchLength();
        int[] nbEndPoints = skeletonResults.getEndPoints();
        int[] junctions = skeletonResults.getJunctions();
        int branches = 0;
        double branchLength = 0;
        int endPoint = 0;
        int junction = 0;
        for (int i = 0; i < skeletonResults.getGraph().length; i++) {
            branches += branchNumbers[i];
            branchLength += branchLengths[i];
            endPoint += nbEndPoints[i];
            junction += junctions[i];
        }
        double[] skeletonParams = {branches, branchLength, endPoint, junction};
        FileSaver imgSave = new FileSaver(imgLabProj);
        imgSave.saveAsTiff(outDir+outFileName+"_"+roiPt.getName()+"_LabelledSkel.tif");
        flush_close(imgLabProj); 
        // Shool Analyse
        intersectionAnalysis(imgBin, roiPt, outFileName, outDir);
        flush_close(imgBin);
        return(skeletonParams);
    }
    
    
    /**
     * Get Roi volume
     */
    public double roiVolume(Roi roi, ImagePlus img) {
        int z = img.getNSlices();
        img.setRoi(roi);
        img.updateAndDraw();
        double area = img.getStatistics(Measurements.AREA).area;
        return(area * cal.pixelDepth);
    }
    
    /**
    * Local compute parameters add to results file
    * @param mitoPop mito population
    * @param mitoParams branch number, branch lenght, end points, junctions
     * @param imgName
    * @param results buffer
    * @throws java.io.IOException
    **/
    public void computeParameters(Objects3DPopulation mitoPop, ImagePlus imgMito, double[] mitoParams, Roi roiPoly, Roi roiPt, 
            String imgName, BufferedWriter results) throws IOException {
        IJ.showStatus("Computing parameters ....");
        // mito volume
        double mitoVol = 0;
        double mitoInt = 0;
        double roiVol = roiVolume(roiPoly, imgMito);
        int mitos = mitoPop.getNbObjects();
        for (int i = 0; i < mitos; i++)
            mitoVol += mitoPop.getObject(i).getVolumeUnit();
            
        results.write(imgName+"\t"+roiPoly.getName()+"\t"+roiVol+"\t"+mitos+"\t"+mitoVol+"\t"+mitoParams[0]+"\t"+mitoParams[1]+"\t"+mitoParams[2]+"\t"+
                mitoParams[3]+"\n");
        results.flush();
    }
    
    public void intersectionAnalysis(ImagePlus img, Roi roiPt, String imgName, String outDir) throws IOException {
        final int n_cpus = Prefs.getThreads();
        // radius of astrocyte soma
        double astroRad = 10*cal.pixelWidth;
        double shollStep = 5*cal.pixelWidth;
        PointRoi shollCenter = new PointRoi(roiPt.getFloatPolygon());
        int dx = ((int)roiPt.getXBase() <= img.getWidth()/2) ? ((int)roiPt.getXBase()-img.getWidth()) : (int)roiPt.getXBase();
        int dy = ((int)roiPt.getYBase() <= img.getHeight()/2) ? ((int)roiPt.getYBase()-img.getHeight()) : (int)roiPt.getYBase();
        int dz = roiPt.getZPosition();
        double maxEndRadius = Math.sqrt(dx*dx + dy*dy + dz*dz)*cal.pixelWidth;
        img.setZ(roiPt.getZPosition() );
        img.setRoi(shollCenter, true);
        IJ.run("Legacy: Sholl Metrics & Options...", "spatial threshold center starting radius samples enclosing intersecting sum mean median skewness kurtosis "
            + "centroid p10-p90 append=[] plots=[Only linear profile] background=0 preferred=[Fitted values] parallel="+n_cpus+" file=.csv decimal=3");
        img.setTitle(imgName+"_"+roiPt.getName());
        img.show();
        IJ.run(img, "Legacy: Sholl Analysis (From Image)...", "starting="+astroRad+" ending="+maxEndRadius+" radius_step="+shollStep+" #_samples=1 integration=Mean"
                + "enclosing=1 #_primary=5 infer fit linear polynomial=[Best fitting degree] most normalizer=Volume save directory="+outDir+" do");    
        img.hide();
        WindowManager.getWindow("Sholl Results").dispose();
    }
}
