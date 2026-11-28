import { poolEmbedding } from "./llm.ts";

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("poolEmbedding normalizes a single 1024-dimension vector", () => {
  const vector = poolEmbedding(Array.from({ length: 1024 }, () => 2));
  assert(vector !== null, "expected a vector");
  assert(vector.length === 1024, "expected 1024 dimensions");
  assert(Math.abs(vector[0] - 1 / 32) < 1e-10, "expected L2-normalized vector");
});

Deno.test("poolEmbedding mean-pools token embeddings", () => {
  const first = Array.from({ length: 1024 }, () => 2);
  const second = Array.from({ length: 1024 }, () => 4);
  const vector = poolEmbedding([first, second]);
  assert(vector !== null, "expected pooled vector");
  assert(vector.length === 1024, "expected 1024 dimensions");
  assert(Math.abs(vector[17] - 1 / 32) < 1e-10, "expected normalized mean");
});

Deno.test("poolEmbedding accepts batch-wrapped token output", () => {
  const rows = [
    Array.from({ length: 1024 }, () => 1),
    Array.from({ length: 1024 }, () => 1),
  ];
  const vector = poolEmbedding([rows]);
  assert(vector !== null, "expected batch-wrapped vector");
  assert(Math.abs(vector[0] - 1 / 32) < 1e-10, "expected normalized result");
});

Deno.test("poolEmbedding rejects invalid dimensions and non-finite values", () => {
  assert(poolEmbedding([1, 2, 3]) === null, "short vector should be rejected");
  const invalid = Array.from({ length: 1024 }, () => 1);
  invalid[18] = Number.NaN;
  assert(poolEmbedding(invalid) === null, "NaN should be rejected");
});
