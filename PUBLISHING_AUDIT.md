# Publishing audit

Status: not release-ready.

Before the first public release:

- Complete independent scientific validation against analytic examples and an
  established spatial-statistics implementation.
- Test the real Fiji dialogs, macro recorder, plots, cancellation, ROI input,
  and folder batch workflow.
- Benchmark large object counts and 999-simulation batches.
- Add user-facing statistical interpretation and misuse warnings.
- Confirm repository/update-site name availability again at release time.
- Add the ImageJ update-site publishing workflow and credentials only as GitHub
  repository secrets.
- Remove `-SNAPSHOT`, update the changelog and citation metadata, then perform a
  clean build from a fresh checkout.
