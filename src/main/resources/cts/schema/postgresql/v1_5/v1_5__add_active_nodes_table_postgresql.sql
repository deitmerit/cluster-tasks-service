CREATE TABLE IF NOT EXISTS cts_active_nodes (
  CTSAN_NODE_ID     CHARACTER VARYING(40)   CONSTRAINT ctsan_pk PRIMARY KEY,
  CTSAN_SINCE       TIMESTAMP               NOT NULL,
  CTSAN_LAST_SEEN   TIMESTAMP               NOT NULL
);