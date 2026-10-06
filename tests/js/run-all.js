/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
"use strict";
// Runs every tests/js/*.test.js file with Node's built-in test runner. No npm install needed.
// Used instead of "node --test <folder>" because that syntax differs between Node versions.
//
//   node tests/js/run-all.js        (or: npm test)
const fs = require("node:fs");
const path = require("node:path");

for (const file of fs.readdirSync(__dirname).filter((n) => n.endsWith(".test.js")).sort()) {
  require(path.join(__dirname, file));
}
