/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.model.Route;

import java.util.Locale;

/** Who may change which route. One place, so the depot's Routes tab and the Route Finder can never disagree. */
public final class RouteAccess {

    private RouteAccess() {}

    public static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public static boolean same(String a, String b) {
        return !norm(a).isEmpty() && norm(a).equals(norm(b));
    }

    /** The depot owns the route (it created it), or an admin-created route is registered to START at this depot. */
    public static boolean canEdit(Route r, String depot) {
        if (r.getOwnerDepot() != null) return same(r.getOwnerDepot(), depot);
        return same(r.getSourceDepot(), depot);
    }

    /** Only the depot's own routes; an admin-created route can only be deleted by the admin. */
    public static boolean canDelete(Route r, String depot) {
        return same(r.getOwnerDepot(), depot);
    }
}
