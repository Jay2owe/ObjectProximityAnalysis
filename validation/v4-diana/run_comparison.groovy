#@ String dir
// V4 - run Object Proximity Analysis and DiAna on identical label images.
//
// Usage (headless, in a Fiji with OPA plus the DistanceAnalysis and
// 3D ImageJ Suite update sites):
//   fiji --headless --run run_comparison.groovy "dir='<scene directory>/'"
//
// Reads the scenes written by make_scenes.ijm and writes <dir>/comparison.csv:
// one row per object in A and tool, with its nearest partner in B and the
// centre-centre, centre-edge, edge-centre and edge-edge distances (calibrated)
// and the contact measure each tool reports.
//
// DiAna side. DiAna 1.54's macro wrapper ("DiAna_Analyse") returns without a
// results table when Fiji runs headless or with -batch, so this script calls
// the class that does DiAna's distance work, DiAna.Measures, the way
// DiAna_Analyse does (ComputeAdjacency with k = 1, closest object by centre).
// If that method's AdjacencyResults table cannot be retrieved headless, the
// script makes the identical mcib3d calls ComputeAdjacency makes, read from its
// bytecode: distCenterUnit (centre-centre), distCenterBorderUnit A->B
// (centre-edge), distCenterBorderUnit B->A (edge-centre), distBorderUnit
// (edge-edge), and surfaceContact(B, 0)[0] + [1] (contact). The "source" column
// records which route produced each row.

import ij.IJ
import ij.ImagePlus
import ij.measure.ResultsTable
import mcib3d.geom.Objects3DPopulation
import mcib3d.image3d.ImageInt
import opa.OPA
import opa.OPAParameters
import opa.internal.engine.DistanceMode

def out = new StringBuilder(
        "scene,tool,source,label_A,partner_B,centre_centre,centre_edge,edge_centre,edge_edge,contact,contact_unit\n")

for (scene in ["spheres", "blobs"]) {
    ImagePlus a = IJ.openImage(dir + scene + "_A.tif")
    ImagePlus b = IJ.openImage(dir + scene + "_B.tif")
    a.setTitle("A"); b.setTitle("B")

    // ---- OPA ----
    def parameters = OPAParameters.builder(a, b)
            .channelNames(["A", "B"])
            .runDistances(true)
            .runPattern(false)
            .includeSelfDistances(false)
            .distanceModes(EnumSet.allOf(DistanceMode))
            .neighborCount(1)
            .contactDistance(0.0d)
            .build()
    def result = OPA.run(parameters)
    result.getPerObjectTables().each { key, ResultsTable table ->
        if (!(key.startsWith("A") && key.contains("B"))) return
        for (int row = 0; row < table.size(); row++) {
            def v = { String mode -> table.getValue(mode + "_NN1_Value", row) }
            def partner = (int) table.getValue("Centre-Centre_NN1_Partner_Label", row)
            out.append([scene, "OPA", "OPA.run", (int) table.getValue("Source_Label", row),
                        partner, v("Centre-Centre"), v("Centre-Edge"), v("Edge-Centre"),
                        v("Edge-Edge"), table.getValue("Surface-Contact_NN1_Exact_Contact", row),
                        table.getStringValue("Surface_Measure_Unit", row)].join(",")).append("\n")
        }
    }

    // ---- DiAna ----
    def ia = ImageInt.wrap(a)
    def ib = ImageInt.wrap(b)
    def popA = new Objects3DPopulation(ia)
    def popB = new Objects3DPopulation(ib)
    ResultsTable adjacency = null
    try {
        def measures = Class.forName("DiAna.Measures").getConstructors()[0]
                .newInstance(ia, ib, popA, popB)
        measures.ComputeAdjacency(1, true, true, true, true, true, true, 0.0d)
        adjacency = ResultsTable.getResultsTable("AdjacencyResults")
    } catch (Throwable failure) {
        IJ.log("DiAna Measures.ComputeAdjacency: " + failure)
    }
    for (int i = 0; i < popA.getNbObjects(); i++) {
        def objA = popA.getObject(i)
        def objB = popB.closestCenter(objA, 0.005d)
        int[] contact = objA.surfaceContact(objB, 0.0d)
        String source = "mcib3d calls of Measures.ComputeAdjacency"
        double cc = objA.distCenterUnit(objB)
        double ee = objA.distBorderUnit(objB)
        double ce = objA.distCenterBorderUnit(objB)
        double ec = objB.distCenterBorderUnit(objA)
        double sc = contact[0] + contact[1]
        if (adjacency != null && adjacency.size() == popA.getNbObjects()) {
            source = "DiAna Measures.ComputeAdjacency"
            cc = adjacency.getValue("Dist CenterA-CenterB", i)
            ee = adjacency.getValue("Dist min EdgeA-EdgeB", i)
            ce = adjacency.getValue("Dist min CenterA-EdgeB", i)
            ec = adjacency.getValue("Dist min EdgeA-CenterB", i)
            sc = adjacency.getValue("Surface contact", i)
        }
        out.append([scene, "DiAna", source, (int) objA.getValue(), (int) objB.getValue(),
                    cc, ce, ec, ee, sc, "voxel faces"].join(",")).append("\n")
    }
}
new File(dir + "comparison.csv").text = out.toString()
IJ.log("V4 comparison written to " + dir + "comparison.csv")
