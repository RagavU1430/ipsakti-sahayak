# A/B Hash Verification

Run timestamp: 2026-09-02 (per session date)

| Slot | Expected SHA-256 | Observed SHA-256 | Line count | Match? |
|------|------------------|------------------|------------|--------|
| OLD  | `4ce211289e88958c89d4bafc4ede7271cc1f18b73acbe9ea30131bda` | NOT FOUND (no local copy) | unknown | ❌ NO MATCH |
| NEW  | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` | 7019 | ✅ EXACT MATCH |

## Files inspected and their hashes

| File | SHA-256 |
|------|---------|
| `ip-sakti-rag/dataset/canonical/chunks.jsonl` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` (NEW) |
| `ip-sakti-rag/.audit_tmp/ab_eval/new/chunks.jsonl` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` (NEW copy) |
| `ip-sakti-rag/.audit_tmp/ab_eval/old/chunks.jsonl` | `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd` (NOT canonical old) |
| `ip-sakti-rag/dataset/processed/chunks.jsonl` | `aad411456242d63879c2c337222350e212d6af0d76e5bc99ebc0be1710068bde` (NOT canonical old) |

## Conclusion

Only the **NEW** canonical dataset is recoverable. The **OLD** canonical dataset (hash `4ce211...`) is **not present** in any existing local artifact, and the only local "old" copy (`61c6...`) is a different file. Per the master-prompt STEP 1 rule, A/B evaluation cannot proceed against the *exact* historical bytes; reconstruction is forbidden.

STOP condition triggered.
