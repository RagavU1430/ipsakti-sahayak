# Dataset

The MVP registry is a curated set of 25 records. Static legal texts, contextual government publications, international treaties, and restricted/live resources are distinguished with domain, subdomain, document type, access type, and status metadata.

Raw files live below `dataset/raw/<authority>/` locally and are excluded from Git. Reproducible manifests and processed JSONL are committed. A record is not eligible for processing until its bytes pass header/opening-title/checksum validation. An official landing page is not treated as proof that a direct file was downloaded.

Status values are limited to those defined in the project brief. `content_available=true` records the acquisition fact independently from later validation status. An image-only cover is acceptable when extractable title matter appears in the first three pages; a document whose opening matter cannot be extracted is rejected for manual inspection.

The current dataset is an acquisition baseline, not a representation that every law is current. Version and uncertainty notes must be reviewed before legal use.

