// Nod Button Box — 3 buttons (Yes / Yes-Always / No)
// Arduino Nano Every + SSD1306 OLED 0.91" + 3x 12mm tactile buttons
// Print with 0.2mm layer height, 3 walls, 15% infill

// ── Tunables ───────────────────────────────────────────────────────────
box_w       = 90;   // mm width
box_d       = 55;   // mm depth
box_h       = 28;   // mm height (body)
wall        = 2.4;  // wall thickness
lid_h       = 3.0;  // lid thickness
btn_d       = 12.5; // button hole diameter
btn_y       = 32;   // Y position of button row from front
oled_w      = 27.0; // OLED cutout width
oled_h      = 12.0; // OLED cutout height
oled_x      = (box_w - oled_w) / 2;
oled_y      = 8;    // from front
usb_w       = 9.0;  // USB-C cutout width
usb_h       = 4.0;
nano_offset = 5;    // from right wall

// Button X positions (evenly spaced, 3 buttons)
btn_x = [box_w * 0.22, box_w * 0.50, box_w * 0.78];

// ── Bottom shell ───────────────────────────────────────────────────────
module bottom_shell() {
  difference() {
    // Outer box
    cube([box_w, box_d, box_h]);

    // Inner cavity
    translate([wall, wall, wall])
      cube([box_w - 2*wall, box_d - 2*wall, box_h]);

    // Button holes (top face)
    for (x = btn_x) {
      translate([x, btn_y, box_h - 0.1])
        cylinder(h = wall + 0.2, d = btn_d, $fn = 40);
    }

    // OLED window (top face)
    translate([oled_x, oled_y, box_h - 0.1])
      cube([oled_w, oled_h, wall + 0.2]);

    // USB-C cutout (right side, near bottom)
    translate([box_w - 0.1, (box_d - usb_w) / 2, wall + 2])
      cube([wall + 0.2, usb_w, usb_h]);

    // Lid snap groove (top rim, 1mm × 1mm)
    translate([wall + 1, wall + 1, box_h - 1])
      cube([box_w - 2*wall - 2, box_d - 2*wall - 2, 1.2]);
  }

  // PCB standoffs (M2, 4 corners)
  standoff_h = 4;
  standoff_d = 5;
  standoff_positions = [
    [wall + 3, wall + 3],
    [box_w - wall - 3 - standoff_d, wall + 3],
    [wall + 3, box_d - wall - 3 - standoff_d],
    [box_w - wall - 3 - standoff_d, box_d - wall - 3 - standoff_d],
  ];
  for (p = standoff_positions) {
    translate([p[0], p[1], wall])
      difference() {
        cylinder(h = standoff_h, d = standoff_d, $fn = 20);
        cylinder(h = standoff_h + 0.1, d = 2.2, $fn = 16); // M2 hole
      }
  }
}

// ── Button labels (embossed, printed separately or etched) ─────────────
// Yes = left (green LED), Yes-Always = centre (yellow), No = right (red)
label_texts = ["YES", "ALWAYS", "NO"];

// ── Lid (snap-fit top) ─────────────────────────────────────────────────
module lid() {
  translate([0, 0, box_h + 5]) { // print separately
    difference() {
      cube([box_w, box_d, lid_h]);
      // Snap lip
      translate([wall + 1, wall + 1, -0.1])
        cube([box_w - 2*wall - 2, box_d - 2*wall - 2, 1.3]);
    }
  }
}

// ── Button cap (print 3× in green/yellow/red filament) ────────────────
module button_cap() {
  cap_d  = 11.5;
  cap_h  = 8;
  stem_d = 6.5;
  stem_h = 4;
  translate([0, 0, 110]) { // offset so they print separately
    difference() {
      union() {
        cylinder(h = cap_h - 2, d = cap_d, $fn = 32);
        translate([0, 0, cap_h - 2])
          cylinder(h = 2, d1 = cap_d, d2 = cap_d - 2, $fn = 32); // chamfer
      }
      translate([0, 0, -0.1])
        cylinder(h = stem_h + 0.1, d = stem_d, $fn = 24); // stem socket
    }
  }
}

// ── Render ─────────────────────────────────────────────────────────────
bottom_shell();
lid();

// Uncomment to preview button caps:
// for (i = [0:2]) translate([btn_x[i], btn_y, 0]) button_cap();
