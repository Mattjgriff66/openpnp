import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpnp.machine.reference.camera.SimulatedUpCamera;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.VisionUtils;

import com.google.common.io.Files;

/**
 * Bottom vision settings can select which up-looking camera they align parts on. Without a
 * selection, the first up-looking camera is used, as before.
 */
public class MultipleBottomCamerasTest {
    static File workingDirectory;

    @BeforeAll
    public static void setup() throws Exception {
        workingDirectory = Files.createTempDir();
        workingDirectory = new File(workingDirectory, ".openpnp");
        FileUtils.copyURLToFile(ClassLoader.getSystemResource("config/ReferenceBottomVisionOffset/packages.xml"),
                new File(workingDirectory, "packages.xml"));
        FileUtils.copyURLToFile(ClassLoader.getSystemResource("config/ReferenceBottomVisionOffset/parts.xml"),
                new File(workingDirectory, "parts.xml"));
        Configuration.initialize(workingDirectory);
        Configuration.get().load();
        Configuration.get().save();
    }

    @BeforeEach
    public void before() throws Exception {
        Configuration.initialize(workingDirectory);
        Configuration.get().load();
    }

    private Camera addSecondUpCamera(Machine machine) throws Exception {
        SimulatedUpCamera camera = new SimulatedUpCamera();
        camera.setName("Bottom 2");
        camera.setHeadOffsets(new Location(LengthUnit.Millimeters, 100, 50, -20, 0));
        machine.addCamera(camera);
        return camera;
    }

    @Test
    public void testCameraSelection() throws Exception {
        Machine machine = Configuration.get().getMachine();
        Camera defaultCamera = VisionUtils.getBottomVisionCamera();
        Camera secondCamera = addSecondUpCamera(machine);

        BottomVisionSettings settings = new BottomVisionSettings("TEST");
        // No camera selected: default (first up-looking) camera.
        assertSame(defaultCamera, VisionUtils.getBottomVisionCamera(settings));
        assertSame(defaultCamera, VisionUtils.getBottomVisionCamera(null));

        // Selected camera.
        settings.setCameraId(secondCamera.getId());
        assertSame(secondCamera, VisionUtils.getBottomVisionCamera(settings));
        assertSame(secondCamera, settings.getCamera());

        // Unknown camera: falls back to default.
        settings.setCameraId("NO-SUCH-CAMERA");
        assertSame(defaultCamera, VisionUtils.getBottomVisionCamera(settings));

        // A down-looking camera cannot be used for bottom vision: falls back to default.
        settings.setCameraId(machine.getDefaultHead().getDefaultCamera().getId());
        assertSame(defaultCamera, VisionUtils.getBottomVisionCamera(settings));

        // Empty selection means default.
        settings.setCameraId("");
        assertEquals(null, settings.getCameraId());

        assertEquals(2, VisionUtils.getBottomVisionCameras().size());
        assertSame(defaultCamera, VisionUtils.getBottomVisionCameras().get(0));
    }

    @Test
    public void testAlignmentLocationUsesSelectedCamera() throws Exception {
        Machine machine = Configuration.get().getMachine();
        Camera defaultCamera = VisionUtils.getBottomVisionCamera();
        Camera secondCamera = addSecondUpCamera(machine);
        ReferenceBottomVision bottomVision = ReferenceBottomVision.getDefault();
        bottomVision.setEnabled(true);
        Nozzle nozzle = machine.getDefaultHead().getDefaultNozzle();
        Part part = Configuration.get().getPart("R0805-1K");
        assertNotNull(part);
        BottomVisionSettings settings = bottomVision.getInheritedVisionSettings(part);
        settings.setEnabled(true);
        Placement placement = new Placement("P1");
        placement.setLocation(new Location(LengthUnit.Millimeters));

        Location location = bottomVision.getLocation(part, null, placement, nozzle);
        assertXyEquals(defaultCamera.getLocation(nozzle), location);

        settings.setCameraId(secondCamera.getId());
        location = bottomVision.getLocation(part, null, placement, nozzle);
        assertXyEquals(secondCamera.getLocation(nozzle), location);
    }

    private static void assertXyEquals(Location expected, Location actual) {
        actual = actual.convertToUnits(expected.getUnits());
        assertEquals(expected.getX(), actual.getX(), 1e-6);
        assertEquals(expected.getY(), actual.getY(), 1e-6);
    }
}
