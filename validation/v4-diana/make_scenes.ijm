// V4 - synthetic 3D label scenes for the OPA versus DiAna comparison.
//
// Usage (headless):
//   fiji --headless --console -macro make_scenes.ijm "<output directory>"
//
// Writes, into the output directory:
//   spheres_A.tif, spheres_B.tif   six ball pairs with known radii and centre
//                                  separations (analytic truth for centre-centre
//                                  and centre-edge), anisotropic calibration
//   spheres_truth.csv              the analytic values
//   blobs_A.tif, blobs_B.tif       twelve irregular blob pairs, touching or
//                                  nearly touching, from a fixed seed
// All images are 16-bit labels, 0 = background, calibration 0.2 x 0.2 x 0.5 um.
// Only this generator is tracked in git; the images are regenerated on demand.

out = getArgument();
if (out == "") exit("Pass the output directory as the macro argument.");
if (!endsWith(out, "/") && !endsWith(out, "\\")) out = out + "/";
File.makeDirectory(out);
setBatchMode(true);

W = 256; H = 128; D = 40;
px = 0.2; pz = 0.5;

// ---- scene 1: ball pairs -------------------------------------------------
// columns: A centre (um), rA, B direction (unit vector), separation d, rB
cx = newArray(8.5, 25.5, 42.5, 8.5, 25.5, 42.5);
cy = newArray(6.4, 6.4, 6.4, 19.2, 19.2, 19.2);
cz = newArray(10, 10, 10, 10, 10, 10);
rA = newArray(2.0, 1.5, 2.0, 2.5, 2.0, 1.0);
rB = newArray(2.0, 2.5, 1.0, 2.5, 1.5, 1.0);
dd = newArray(6.0, 5.5, 4.0, 5.0, 2.5, 7.0);
ux = newArray(1, 0, 0.6, 1, 1, 0.57735);
uy = newArray(0, 1, 0, 0, 0, 0.57735);
uz = newArray(0, 0, 0.8, 0, 0, 0.57735);

newImage("spheres_A", "16-bit black", W, H, D);
calibrate();
for (i = 0; i < 6; i++) ball(cx[i], cy[i], cz[i], rA[i], i + 1);
saveAs("Tiff", out + "spheres_A.tif");
close();

newImage("spheres_B", "16-bit black", W, H, D);
calibrate();
for (i = 0; i < 6; i++)
    ball(cx[i] + dd[i] * ux[i], cy[i] + dd[i] * uy[i], cz[i] + dd[i] * uz[i], rB[i], i + 1);
saveAs("Tiff", out + "spheres_B.tif");
close();

truth = "pair,rA_um,rB_um,centre_separation_um,centre_centre_um,centre_edge_A_to_B_um,edge_centre_A_to_B_um,edge_edge_um\n";
for (i = 0; i < 6; i++) {
    ce = maxOf(0, dd[i] - rB[i]);
    ec = maxOf(0, dd[i] - rA[i]);
    ee = maxOf(0, dd[i] - rA[i] - rB[i]);
    truth = truth + (i + 1) + "," + rA[i] + "," + rB[i] + "," + dd[i] + "," + dd[i] + "," + ce + "," + ec + "," + ee + "\n";
}
File.saveString(truth, out + "spheres_truth.csv");

// ---- scene 2: irregular blobs ---------------------------------------------
// Each object is the union of four small balls around a centre; B sits beside
// A at a gap drawn from {touching, 0.2, 0.6, 1.2 um}. Fixed seed.
random("seed", 20260928);
newImage("blobs_A", "16-bit black", W, H, D);
calibrate();
nb = 12;
bx = newArray(nb); by = newArray(nb); bz = newArray(nb); gap = newArray(nb);
gaps = newArray(0, 0.2, 0.6, 1.2);
for (i = 0; i < nb; i++) {
    bx[i] = 6 + (i % 6) * 8;
    by[i] = 6.4 + floor(i / 6) * 12.8;
    bz[i] = 10;
    gap[i] = gaps[i % 4];
    blob(bx[i], by[i], bz[i], 1.6, i + 1);
}
saveAs("Tiff", out + "blobs_A.tif");
close();
newImage("blobs_B", "16-bit black", W, H, D);
calibrate();
for (i = 0; i < nb; i++) blob(bx[i] + 3.4 + gap[i], by[i], bz[i], 1.4, i + 1);
saveAs("Tiff", out + "blobs_B.tif");
close();
print("V4 scenes written to " + out);

function calibrate() {
    run("Properties...", "channels=1 slices=" + D + " frames=1 pixel_width=" + px
        + " pixel_height=" + px + " voxel_depth=" + pz + " unit=micron");
}

// Voxel (x, y, z) belongs to the ball when its centre, in calibrated units,
// lies within r of (x0, y0, z0). Voxel centres are at (index + 0.5) * size.
function ball(x0, y0, z0, r, label) {
    x1 = floor((x0 - r) / px); x2 = floor((x0 + r) / px) + 1;
    y1 = floor((y0 - r) / px); y2 = floor((y0 + r) / px) + 1;
    z1 = floor((z0 - r) / pz); z2 = floor((z0 + r) / pz) + 1;
    for (z = maxOf(0, z1); z <= minOf(D - 1, z2); z++) {
        setSlice(z + 1);
        vz = (z + 0.5) * pz - z0;
        for (y = maxOf(0, y1); y <= minOf(H - 1, y2); y++) {
            vy = (y + 0.5) * px - y0;
            for (x = maxOf(0, x1); x <= minOf(W - 1, x2); x++) {
                vx = (x + 0.5) * px - x0;
                if (vx * vx + vy * vy + vz * vz <= r * r) setPixel(x, y, label);
            }
        }
    }
}

// Union of four balls of radius 0.55 r to 0.9 r scattered within 0.6 r of the
// centre. Only the x extent toward +x is bounded, so B can be placed beside A.
function blob(x0, y0, z0, r, label) {
    for (k = 0; k < 4; k++) {
        rr = r * (0.55 + 0.35 * random());
        ox = (random() - 0.5) * 1.2 * r;
        oy = (random() - 0.5) * 1.2 * r;
        oz = (random() - 0.5) * 1.2 * r;
        if (ox + rr > r) ox = r - rr;
        if (ox - rr < -r) ox = -r + rr;
        ball(x0 + ox, y0 + oy, z0 + oz, rr, label);
    }
}
