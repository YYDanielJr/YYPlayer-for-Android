package yy.daniel.yyplayerforandroid.audio

/** Recovery is bounded to the same connected route, and never overrides an explicit strict policy. */
object OutputFallbackPolicy {
    fun allowed(sharing: SharingRequest, transparency: TransparencyRequest, expectedRoute: Int?, currentRoute: Int?, routePresent: Boolean): Boolean =
        sharing != SharingRequest.STRICT_EXCLUSIVE && transparency != TransparencyRequest.STRICT_UNPROCESSED &&
            routePresent && expectedRoute != null && expectedRoute == currentRoute
}
