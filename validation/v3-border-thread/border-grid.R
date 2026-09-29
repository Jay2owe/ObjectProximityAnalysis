# V3 open thread: how much of the border-corrected K difference between OPA and
# spatstat comes from spatstat's binned (histogram) construction rather than
# from the intensity convention?
#
# Usage:
#   Rscript border-grid.R <export-directory> [<report-file>]
#
# <export-directory> is the output of opa-core's SpatstatExport (its main()
# takes the directory as its only argument). The same twelve fixed patterns and
# OPA curves as V3 are read back, and spatstat's border estimate is computed on
# the original 20-radius grid and on progressively finer evenly spaced grids
# that start at zero and contain the original radii exactly.
#
# The univariate intensity convention (OPA divides by n-1, spatstat by n; see
# V3_FINDINGS.md) is removed first by rescaling OPA's univariate curve by
# (n-1)/n, so what remains is the construction difference alone. Cross-K has no
# such factor: both implementations divide by the target count.
#
# An exact reduced-sample estimator written directly in R from the definition
# (pairs within r whose anchor is at least r from the boundary) is reported
# alongside as a third, independent reading.

suppressPackageStartupMessages({
  library(spatstat.explore)
  library(spatstat.geom)
})

args <- commandArgs(trailingOnly = TRUE)
root <- if (length(args) > 0) args[1] else "validation/v3-spatstat"
out <- if (length(args) > 1) args[2] else file.path(root, "border-grid-report.txt")
manifest <- read.csv(file.path(root, "manifest.csv"), stringsAsFactors = FALSE)

lines <- c()
say <- function(...) {
  line <- paste0(...)
  lines <<- c(lines, line)
  cat(line, "\n", sep = "")
}

read_pts <- function(name, role) {
  read.csv(file.path(root, "patterns", paste0(name, "_", role, ".csv")))
}
read_curve <- function(name, fn, corr) {
  read.csv(file.path(root, "opa", paste0(name, "__", fn, "__", corr, ".csv")))$value
}

relgap <- function(a, b) {
  keep <- is.finite(a) & is.finite(b) & (abs(a) + abs(b) > 0)
  if (!any(keep)) return(NA_real_)
  max(abs(a[keep] - b[keep]) / pmax(abs(a[keep]), abs(b[keep])))
}

# Exact reduced-sample border estimator from the definition.
exact_border <- function(X, Y, r, univariate, win) {
  b <- bdist.points(X)
  d <- crossdist(X, Y)
  if (univariate) diag(d) <- Inf
  # OPA's convention: a point is not its own neighbour, so n - 1 targets.
  n_target <- if (univariate) npoints(X) - 1 else npoints(Y)
  sapply(r, function(radius) {
    eligible <- b >= radius
    if (!any(eligible)) return(NA_real_)
    pairs <- sum(d[eligible, , drop = FALSE] <= radius)
    area.owin(win) * pairs / (sum(eligible) * n_target)
  })
}

say("V3 open thread - border correction: binned versus exact construction")
say("spatstat.explore ", as.character(packageVersion("spatstat.explore")),
    ", R ", R.version$major, ".", R.version$minor)
say("Relative differences are max over the 20 original radii, OPA univariate")
say("curves rescaled by (n-1)/n so the intensity convention is removed.")
say("")

refinements <- c(1, 2, 5, 10, 50, 200)
header <- sprintf("%-24s %-6s %12s %s", "case", "kind", "exact_R_vs_OPA",
                  paste(sprintf("%10s", paste0("grid/", refinements)), collapse = " "))
say(header)

summary <- matrix(NA_real_, nrow = nrow(manifest), ncol = length(refinements) + 1)
for (row in seq_len(nrow(manifest))) {
  m <- manifest[row, ]
  win <- owin(c(m$xmin, m$xmax), c(m$ymin, m$ymax))
  rr <- read.csv(file.path(root, "patterns", paste0(m$name, "_radii.csv")))$r
  step <- rr[1]
  src <- read_pts(m$name, "source")
  X <- ppp(src$x, src$y, window = win, checkdup = FALSE)
  univariate <- m$kind == "univariate"
  if (univariate) {
    Y <- X
    opa <- read_curve(m$name, "K", "BORDER")
    n <- npoints(X)
    opa_adj <- opa * (n - 1) / n
  } else {
    tgt <- read_pts(m$name, "target")
    Y <- ppp(tgt$x, tgt$y, window = win, checkdup = FALSE)
    opa <- read_curve(m$name, "crossK", "BORDER")
    opa_adj <- opa
  }
  exact <- exact_border(X, Y, rr, univariate, win)
  # The exact R estimator follows OPA's convention, so it is compared with
  # OPA's unscaled curve: this confirms the definition independently of Java.
  exact_vs_opa <- relgap(exact, opa)
  gaps <- sapply(refinements, function(k) {
    fine <- seq(0, max(rr), by = step / k)
    idx <- round(rr / (step / k)) + 1
    if (univariate) {
      K <- Kest(X, r = fine, correction = "border")
    } else {
      XY <- superimpose(a = X, b = Y, W = win)
      K <- Kcross(XY, "a", "b", r = fine, correction = "border")
    }
    relgap(opa_adj, as.numeric(K$border)[idx])
  })
  summary[row, ] <- c(exact_vs_opa, gaps)
  say(sprintf("%-24s %-6s %12.3e %s", m$name, if (univariate) "uni" else "cross",
              exact_vs_opa, paste(sprintf("%10.3e", gaps), collapse = " ")))
}
say("")
say(sprintf("%-31s %12.3e %s", "max over cases", max(summary[, 1], na.rm = TRUE),
            paste(sprintf("%10.3e", apply(summary[, -1, drop = FALSE], 2, max,
                                           na.rm = TRUE)), collapse = " ")))
writeLines(lines, out)
cat("\nReport written to", out, "\n")
