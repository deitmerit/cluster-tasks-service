BEGIN;

--
-- METADATA
--
CREATE TABLE IF NOT EXISTS cluster_task_meta (
  CTSKM_ID               BIGSERIAL             NOT NULL CONSTRAINT ctskm_pk PRIMARY KEY,
  CTSKM_TASK_TYPE        INTEGER               NOT NULL,
  CTSKM_PROCESSOR_TYPE   CHARACTER VARYING(40) NOT NULL,
  CTSKM_UNIQUENESS_KEY   CHARACTER VARYING(40) NOT NULL,
  CTSKM_CONCURRENCY_KEY  CHARACTER VARYING(40),
  CTSKM_ORDERING_FACTOR  BIGINT,
  CTSKM_CREATED          TIMESTAMP             NOT NULL,
  CTSKM_DELAY_BY_MILLIS  BIGINT                NOT NULL,
  CTSKM_STARTED          TIMESTAMP,
  CTSKM_RUNTIME_INSTANCE CHARACTER VARYING(40),
  CTSKM_MAX_TIME_TO_RUN  BIGINT                NOT NULL,
  CTSKM_BODY_PARTITION   INTEGER,
  CTSKM_STATUS           INTEGER               NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ctskm_idx_1
  ON cluster_task_meta (CTSKM_PROCESSOR_TYPE, CTSKM_UNIQUENESS_KEY, CTSKM_STATUS);

--
-- TASKS' BODY - PARTITION 0
--
CREATE TABLE cluster_task_body_p0 (
  CTSKB_ID   BIGINT NOT NULL CONSTRAINT ctskb_pk_p0 PRIMARY KEY,
  CTSKB_BODY TEXT   NOT NULL
);

--
-- TASKS' BODY - PARTITION 1
--
CREATE TABLE cluster_task_body_p1 (
  CTSKB_ID   BIGINT NOT NULL CONSTRAINT ctskb_pk_p1 PRIMARY KEY,
  CTSKB_BODY TEXT   NOT NULL
);

--
-- TASKS' BODY - PARTITION 2
--
CREATE TABLE cluster_task_body_p2 (
  CTSKB_ID   BIGINT NOT NULL CONSTRAINT ctskb_pk_p2 PRIMARY KEY,
  CTSKB_BODY TEXT   NOT NULL
);

--
-- TASKS' BODY - PARTITION 3
--
CREATE TABLE cluster_task_body_p3 (
  CTSKB_ID   BIGINT NOT NULL CONSTRAINT ctskb_pk_p3 PRIMARY KEY,
  CTSKB_BODY TEXT   NOT NULL
);

CREATE FUNCTION insert_task(
  task_type INTEGER,
  processor_type CHARACTER VARYING(40),
  uniqueness_key CHARACTER VARYING(40),
  concurrency_key CHARACTER VARYING(40),
  delay_by_millis BIGINT,
  max_time_to_run BIGINT,
  body_partition INTEGER,
  ordering_factor BIGINT,
  body TEXT) RETURNS BIGINT
AS $$
  DECLARE
  	task_id BIGINT;

  BEGIN
    INSERT INTO cluster_task_meta (CTSKM_TASK_TYPE, CTSKM_PROCESSOR_TYPE, CTSKM_UNIQUENESS_KEY, CTSKM_CONCURRENCY_KEY, CTSKM_DELAY_BY_MILLIS, CTSKM_MAX_TIME_TO_RUN, CTSKM_BODY_PARTITION, CTSKM_ORDERING_FACTOR, CTSKM_CREATED, CTSKM_STATUS)
      VALUES (task_type, processor_type, uniqueness_key, concurrency_key, delay_by_millis, max_time_to_run, body_partition, COALESCE(ordering_factor, (EXTRACT(EPOCH FROM LOCALTIMESTAMP) * 1000)::BIGINT + delay_by_millis), LOCALTIMESTAMP, 0)
      RETURNING CTSKM_ID INTO task_id;

    IF body_partition = 0 THEN
      INSERT INTO cluster_task_body_p0 (CTSKB_ID, CTSKB_BODY) VALUES (task_id, body);
    ELSEIF body_partition = 1 THEN
      INSERT INTO cluster_task_body_p1 (CTSKB_ID, CTSKB_BODY) VALUES (task_id, body);
    ELSEIF body_partition = 2 THEN
      INSERT INTO cluster_task_body_p2 (CTSKB_ID, CTSKB_BODY) VALUES (task_id, body);
    ELSEIF body_partition = 3 THEN
      INSERT INTO cluster_task_body_p3 (CTSKB_ID, CTSKB_BODY) VALUES (task_id, body);
    END IF;

    RETURN task_id;
  END;
$$ LANGUAGE plpgsql;

END;