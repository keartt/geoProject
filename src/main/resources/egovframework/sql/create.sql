
CREATE TABLE IF NOT EXISTS blob_test (
    layerName varchar(20) NOT NULL,
    bdata bytea NULL
);

CREATE TABLE IF NOT EXISTS ids (
    table_name varchar(100) NOT NULL,
    next_id varchar(10) NULL
);

CREATE TABLE IF NOT EXISTS MFILE_MNG (
    atchfile_id varchar(20) NOT NULL,
    atchfile_sn varchar(10) NOT NULL,
    atchfile_nm varchar(300) NULL,
    atchfile_orgnl_nm varchar(300) NULL,
    atchfile_path varchar(2000) NULL,
    atchfile_encd varchar(20) NULL,
    atchfile_extn varchar(5) NULL,
    atchfile_sz numeric(10) NULL,
    del_yn bpchar(1) NULL,
    reg_id varchar(20) NULL,
    reg_dt timestamp NULL,
    mdfcn_id varchar(20) NULL,
    mdfcn_dt timestamp NULL,
    CONSTRAINT file_mng PRIMARY KEY (atchfile_id, atchfile_sn)
);

CREATE TABLE IF NOT EXISTS mmap_layer_info (
    layer_id varchar(200) NULL,
    user_id varchar(200) NULL,
    title varchar(200) NULL,
    contents varchar(200) NULL
);

CREATE TABLE IF NOT EXISTS mmap_layer_pubdep (
    layer_id varchar(200) NULL,
    dept_id varchar(200) NULL
);