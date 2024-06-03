package qupath.ext.qupip.classes;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThresholdOtsu {

    private static Map<String, Object> params = Map.of(
            "Roi", "Region*",
            "ChannelExtrMethod", "OpticalDensitySum",
            "stainName", "OpticalDensitySum",
            "setPathClass", "Vessels",
            "MinFragment", "1000.0",
            "MaxHole", "1000.0",
            "Downsample", 3.0,
            "gaussianSigma", 15.0
    );

    String RoiPathClass = params.containsKey("Roi") ? params.get("Roi").toString() : null;
    String channelExtractionMethod = params.get("ChannelExtrMethod").toString();
    String stainName = params.get("stainName").toString();
    String setPathClass = params.get("setPathClass").toString();


    ImageData<BufferedImage> imageData = QP.getCurrentImageData();
    ImageServer<BufferedImage> server = imageData.getServer();
    PixelCalibration cal = server.getPixelCalibration();
    double resolutionDownsampleFactor = (double) params.get("Downsample");
    double requestedPixelSizeMicrons = cal.getAveragedPixelSizeMicrons() * resolutionDownsampleFactor;
    ColorDeconvolutionStains stains = getStains();

    PathImage<ImagePlus> pathImage;
    PathObjectHierarchy hierarchy;
    PathAnnotationObject regionAnnotation;

    /**
     * This method applies a threshold to each region of an image.
     * It first updates the image's hierarchy, then computes the regions of the image.
     * For each region, it applies a threshold to the region.
     *
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread execution is interrupted.
     */
    public void thresholdRegions() throws IOException, InterruptedException {
        // Update the image's hierarchy
        updateHierarchy();
        // Compute the regions of the image
        for (PathImage<ImagePlus> pathImage : computeRegions()) {
            this.pathImage = pathImage;
            // Apply a threshold to the region
            this.thresholdIp(this.getStainIp(pathImage));
        }
    }

    private void updateHierarchy() {
        QP.fireHierarchyUpdate();
        hierarchy = imageData.getHierarchy();
    }

    /**
     * This method computes the regions of an image that need to be processed.
     * It iterates over all annotation objects and checks if the name of the path class of the current object
     * matches the value of the "Roi" key in the params map. If it does, the method processes the object and adds it to a list.
     * If the "Roi" key is not present in the params map (i.e., RoiPathClass is null), the method processes a null object and adds it to the list.
     * The method returns the list of processed objects.
     *
     * @return A list of PathImage<ImagePlus> objects representing the regions of the image that need to be processed.
     * @throws IOException If an I/O error occurs.
     */
    private List<PathImage<ImagePlus>> computeRegions() throws IOException {
        List<PathImage<ImagePlus>> pathImageList = new ArrayList<>();
        // Iterate over all annotation objects
        for (PathObject roi : QP.getAnnotationObjects()) {
            regionAnnotation = (PathAnnotationObject) roi;
            // Check if the name of the path class of the current object matches the value of the "Roi" key in the params map
            if (roi.getPathClass().getName().equals(RoiPathClass)) {
                // Process the object and add it to the list
                pathImageList.add(this.processRoi(roi));
            }
        }
        // If the "Roi" key is not present in the params map (i.e., RoiPathClass is null), process a null object and add it to the list
        if (RoiPathClass == null) {
            pathImageList.add(this.processRoi(null));
        }
        // Return the list of processed objects
        return pathImageList;
    }

    /**
     * This method processes a PathObject and returns a PathImage<ImagePlus> object.
     * It first checks if the PathObject is not null and gets its ROI (Region of Interest).
     * Then, it checks if the ROI is null. If it is, it creates a RegionRequest for the entire image.
     * If the ROI is not null, it creates a RegionRequest for the specific ROI.
     * Finally, it converts the RegionRequest to a PathImage<ImagePlus> object and returns it.
     *
     * @param roi The PathObject to be processed. If null, a RegionRequest for the entire image will be created.
     * @return A PathImage<ImagePlus> object representing the region of the image that needs to be processed.
     * @throws IOException If an I/O error occurs.
     */
    private PathImage<ImagePlus> processRoi(PathObject roi) throws IOException {
        ROI roiObj = roi != null ? roi.getROI() : null;
        RegionRequest region = roiObj == null ?
                RegionRequest.createInstance(
                        server.getPath(),
                        resolutionDownsampleFactor,
                        0, 0,
                        this.server.getWidth(), this.server.getHeight()
                ) :
                RegionRequest.createInstance(server.getPath(), resolutionDownsampleFactor, roiObj);
        return IJTools.convertToImagePlus(server, region);
    }

    /**
     * This method retrieves the ImageProcessor for the specified stain from the given PathImage.
     * It first gets the index of the stain by calling the getStainIndex() method.
     * If the stain index is less than 0, it means the stain was not found and the method returns null.
     * Otherwise, it gets the ImageProcessor from the PathImage and checks the extraction method specified in the params map.
     * If the extraction method is "Deconvolution", it performs color deconvolution on the ImageProcessor and returns the ImageProcessor for the specified stain.
     * If the extraction method is "OpticalDensitySum", it converts the ImageProcessor to an optical density sum and returns it.
     * If none of the above conditions are met, the method returns null.
     *
     * @param pathImage The PathImage from which to retrieve the ImageProcessor.
     * @return The ImageProcessor for the specified stain, or null if the stain was not found or the extraction method is not supported.
     */
    private ImageProcessor getStainIp(PathImage<ImagePlus> pathImage) {
        ImageProcessor ip = pathImage.getImage().getProcessor();

        if (channelExtractionMethod.equals("OpticalDensitySum")) {
            return IJTools.convertToOpticalDensitySum(ip.convertToColorProcessor(), 255, 255, 255);

        } else if (channelExtractionMethod.equals("Deconvolution")) {
            int stainIndex = this.getStainIndex();
            if (stainIndex < 0) {
                System.out.println("Could not find stain with name " + stainName + " !");
                return IJTools.colorDeconvolve(ip.convertToColorProcessor(), stains)[stainIndex].duplicate();
            }
        }
        return null;
    }

    private int getStainIndex() {
        for (int i = 0; i < 3; i++) {
            if (stains.getStain(i + 1).getName().equals(stainName)) {
                return i;
            }
        }
        if (channelExtractionMethod.equals("OpticalDensitySum")) {
            stainName = "OpticalDensitySum";
        }
        return -1;
    }


    /**
     * This method applies a threshold to the ImageProcessor of a stain.
     * It first applies a Gaussian blur to the ImageProcessor.
     * Then, it sets an auto threshold to the ImageProcessor.
     * After that, it creates a selection from the ImageProcessor and makes measurements on the selection.
     * It creates an annotation from the ImageProcessor, the selection, and the measurements.
     * The annotation is then added to the hierarchy.
     * Finally, it refines the annotations.
     *
     * @param ipStain The ImageProcessor of the stain to which the threshold will be applied.
     * @throws InterruptedException If the thread execution is interrupted.
     */
    private void thresholdIp(ImageProcessor ipStain) throws InterruptedException {
        applyGaussianBlur(ipStain);
        setAutoThreshold(ipStain);
        Roi roiIJ = createSelection(ipStain);
        ImageStatistics stats = this.makeMeasurements(ipStain, roiIJ);
        PathObject annotation = createAnnotation(ipStain, roiIJ, stats);
        addAnnotationToHierarchy(annotation);
        refineAnnotations();
    }

    private void applyGaussianBlur(ImageProcessor ipStain) {
        double sigmaMicrons = (double) params.get("gaussianSigma");
        double sigmaPixels = sigmaMicrons / requestedPixelSizeMicrons;
        if (sigmaPixels > 0) {
            GaussianBlur gaussianBlur = new GaussianBlur();
            gaussianBlur.blurGaussian(ipStain, sigmaPixels);
        }
    }

    private void setAutoThreshold(ImageProcessor ipStain) {
        AutoThresholder.Method thresholdMethod = AutoThresholder.Method.Otsu;
        ipStain.setAutoThreshold(thresholdMethod, true);
    }

    private Roi createSelection(ImageProcessor ipStain) {
        ThresholdToSelection tts = new ThresholdToSelection();
        return tts.convert(ipStain);
    }

    private ImageStatistics makeMeasurements(ImageProcessor ipStain, Roi roiIJ) {
        ipStain.setRoi(roiIJ);
        ImagePlus imp = pathImage.getImage();
        return ImageStatistics.getStatistics(
                ipStain,
                ImageStatistics.MEAN + ImageStatistics.MIN_MAX + ImageStatistics.AREA,
                imp.getCalibration());
    }

    private PathObject createAnnotation(ImageProcessor ipStain, Roi roiIJ, ImageStatistics stats) {
        ROI roi = IJTools.convertToROI(roiIJ, pathImage);
        PathObject annotation = PathObjects.createAnnotationObject(roi);
        MeasurementList measurementList = annotation.getMeasurementList();
        measurementList.put("Threshold (IJ)", ipStain.getMinThreshold());
        measurementList.put("Area (IJ)", stats.area);
        measurementList.put("Mean " + stainName + " (IJ)", stats.mean);
        measurementList.put("Min " + stainName + " (IJ)", stats.min);
        measurementList.put("Max " + stainName + " (IJ)", stats.max);
        measurementList.close();
        annotation.setLocked(true);
        return annotation;
    }

    private void addAnnotationToHierarchy(PathObject annotation) {
        if (RoiPathClass != null) {
            hierarchy.addObjectBelowParent(
                    regionAnnotation,
                    annotation,
                    true
            );
        } else {
            hierarchy.addObject(
                    annotation,
                    false);
        }
        annotation.setPathClass(
                QP.getPathClass(setPathClass)
        );
        QP.selectObjectsByClassification(
                setPathClass
        );
    }

    private void refineAnnotations() throws InterruptedException {
        String pluginArgs = String.format("{\"minFragmentSizeMicrons\": %s,  \"maxHoleSizeMicrons\": %s}",
                params.get("MinFragment"), params.get("MaxHole"));
        QP.runPlugin("qupath.lib.plugins.objects.RefineAnnotationsPlugin", pluginArgs);
    }

    private ColorDeconvolutionStains getStains() {
        if (isImageValid()) {
            return imageData.getColorDeconvolutionStains();
        }
        System.out.println("An 8-bit RGB brightfield image is required!");
        return null;
    }

    private boolean isImageValid() {
        return server.isRGB() && imageData.isBrightfield() && imageData.getColorDeconvolutionStains() != null;
    }
}