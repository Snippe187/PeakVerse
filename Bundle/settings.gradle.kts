rootProject.name = "bundle"

// Das All-in-One-Plugin: der Core und alle eingebauten Module werden zu einem einzigen Jar (PeakCore-<Version>.jar) zusammengesetzt.
// Die Module kommen als Composite Build aus ihren eigenen Ordnern.
includeBuild("../PeakCore")
includeBuild("../PeakSound")
