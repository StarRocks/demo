# Project instructions

This is a live demo of Claude Code answering plain-English questions about the Olist
Brazilian E-Commerce dataset via the StarRocks and MinIO (AIStor) MCP servers. The single
goal of these instructions is **transparency** — everything shown should be visibly reasoned
from the live data, not recalled or precomputed. There are deliberately no canned queries,
no expected answers, and no schema hints in this repo.

## Show your work on every data question

For any question that requires querying the StarRocks `olist` database:

1. **State the query in prose first** — say what you're about to run and, for joins, name the
   join keys you're using (e.g. "joining `orders` to `order_items` on `order_id`").
2. **Execute it through the MCP tool** — run the actual `read_query` (or other MCP) call so
   the tool invocation and its full SQL are visible in the transcript. Do not collapse,
   paraphrase, or summarize the SQL away.
3. **Answer from the returned rows** — base the answer on the live result set. If a result is
   surprising, show the raw numbers, not just the interpretation.

## Ground truth is the live schema, not memory

The Olist dataset is a well-known public dataset, so treat any recalled knowledge about it as
a *hint to verify*, never as fact:

- Base join keys and column names on the live schema you inspect, not on memory.
- If a query depends on something not provable from the schema alone (key uniqueness,
  cardinality, whether a distinction like `customer_id` vs `customer_unique_id` matters), run
  a quick check (`COUNT(DISTINCT ...)`, sample rows, before/after row counts) rather than
  assume.
- If you do rely on a recalled convention, flag it so it can be checked.
