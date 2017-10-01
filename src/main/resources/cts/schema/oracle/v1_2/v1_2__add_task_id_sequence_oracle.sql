DECLARE
  MaxTaskId NUMBER(19);

BEGIN
  SELECT COALESCE(MAX(CTSKM_ID), 0) + 1000
  INTO MaxTaskId
  FROM CLUSTER_TASK_META;

  EXECUTE IMMEDIATE 'CREATE SEQUENCE CLUSTER_TASK_ID START WITH ' || MaxTaskId || ' INCREMENT BY 1 CACHE 500';
END;

