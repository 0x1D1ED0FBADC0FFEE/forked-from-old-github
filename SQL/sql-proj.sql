--A.S., CSE373, Project Spring 2023

--This is written for oracle's PL/SQL environment
--the below drop commands can be uncommented to clear things up.



-- drop tables
--drop table requires;
--drop table assign;
--drop table proficient;
--drop table project;
--drop table pls;
--drop table mts;

--make tables

CREATE TABLE mts (
	id NUMBER(3) PRIMARY KEY,
	mts_name CHAR(20) NOT NULL,
	gender CHAR(1) CHECK (gender IN ('m', 'f')),
	salary NUMBER(10),
	sup_id NUMBER(3),
	wse_not_admin NUMBER(1) CHECK (wse_not_admin IN (0, 1)),
	CONSTRAINT fk_sup FOREIGN KEY (sup_id) REFERENCES mts(id)
);

CREATE TABLE pls (
	pls_name char(20) primary key,
	num_projects_requiring number(10) not null
);

CREATE TABLE project (
	proj_name CHAR(20) PRIMARY key,
	start_date DATE NOT NULL,
	completion_date DATE,
	id NUMBER(3) NOT NULL,
	CONSTRAINT fk_mts FOREIGN KEY (id) REFERENCES mts(id)
);


CREATE TABLE proficient (
	id NUMBER(3) NOT NULL,
	pls_name CHAR(20) NOT NULL,
	degree_cert CHAR(20) NOT NULL,
	date_degree_cert DATE,
	CONSTRAINT fk_mts_prof FOREIGN KEY (id) REFERENCES mts(id),
	CONSTRAINT fk_pls_prof FOREIGN KEY (pls_name) REFERENCES pls(pls_name)
);

CREATE TABLE assign (
	id NUMBER(3) NOT NULL,
	proj_name CHAR(20) NOT NULL,
	pls_name CHAR(20) NOT NULL,
	assign_start_date DATE NOT NULL,
	assign_end_date DATE,
	CONSTRAINT fk_mts_assign FOREIGN KEY (id) REFERENCES mts(id),
	CONSTRAINT fk_proj_assign FOREIGN KEY (proj_name) REFERENCES project(proj_name),
	CONSTRAINT fk_pls_assign FOREIGN KEY (pls_name) REFERENCES pls(pls_name)
);


CREATE TABLE requires (
	proj_name CHAR(20) NOT NULL,
	pls_name CHAR(20) NOT NULL,
	CONSTRAINT fk_proj_req FOREIGN KEY (proj_name) REFERENCES project(proj_name),
	CONSTRAINT fk_pls_req FOREIGN KEY (pls_name) REFERENCES pls(pls_name)
);

--add constraints
ALTER TABLE pls ADD (
	CONSTRAINT num_proj_req CHECK (num_projects_requiring = (
		SELECT COUNT(*) FROM requires WHERE requires.pls_name = pls.pls_name
	))
);

--add trigger
CREATE TRIGGER proficient_check
BEFORE INSERT OR UPDATE ON assign
FOR EACH ROW
DECLARE
	cnt NUMBER;
BEGIN
	SELECT COUNT(*) INTO cnt FROM proficient
	WHERE proficient.id = :new.id AND proficient.pls_name = :new.pls_name;
		IF cnt = 0 THEN
			RAISE_APPLICATION_ERROR(-20500, 'Dev not proficient in required pls.');
		END IF;
END;
/


--procedures first so the database can be populated


--create new mts, id sequence starts at 10 so 0 through 9 can be used for debugging

CREATE SEQUENCE mts_id_seq
	START WITH 10
	INCREMENT BY 1
	NOCACHE;


CREATE PROCEDURE hire_mts(
	p_name IN CHAR,
	p_gender IN CHAR,
	p_salary IN NUMBER,
	p_supervisor_id IN NUMBER
) AS
BEGIN
	INSERT INTO mts (
		id,
		mts_name,
		gender,
		salary,
		sup_id,
		wse_not_admin
	)
	VALUES (
		mts_id_seq.NEXTVAL,
		p_name,
		p_gender,
		p_salary,
		p_supervisor_id,
		1
	);
	COMMIT;
END hire_mts;
/

--add pls to db

CREATE PROCEDURE add_pls(
	p_pls_name IN CHAR
) AS
BEGIN
	INSERT INTO pls (
		pls_name,
		num_projects_requiring
	)
	VALUES (
		p_pls_name,
		0
	);
	COMMIT;
END add_pls;
/


CREATE PROCEDURE add_project_req(
	p_proj_name IN CHAR,
	p_pls_name IN CHAR
) AS
	v_project_count NUMBER;
BEGIN
	--does project exist
	SELECT COUNT(*) INTO v_project_count
	FROM project
	WHERE proj_name = p_proj_name;

	--if not, insert
	IF v_project_count = 0 THEN
		INSERT INTO project (
			proj_name,
			start_date,
			id
		)
		VALUES (
			p_proj_name,
			SYSDATE,
			NULL
		);
	END IF;
END;
/


  --update pls requirements

INSERT INTO requires (
	proj_name,
    pls_name
)
VALUES (
    p_proj_name,
    p_pls_name
);

COMMIT;
END add_project_req;
/

--see if pls can be retired

CREATE PROCEDURE check_retire_pls(
	p_pls_name IN CHAR
) AS
	v_project_name CHAR(20);
	v_wse_id NUMBER;
	v_wse_name CHAR(20);
	v_primary_proj_name CHAR(20);
BEGIN
	--is pls required by any project
	SELECT proj_name INTO v_project_name
	FROM requires
	WHERE pls_name = p_pls_name
	AND ROWNUM = 1;

	IF v_project_name IS NOT NULL THEN
		DBMS_OUTPUT.PUT_LINE('PLS required by project: ' || v_project_name);

ELSE
		--if retiring would leave a developer without proficiencies
		SELECT id, mts_name INTO v_wse_id, v_wse_name
		FROM mts
		WHERE id NOT IN (
			SELECT id
			FROM proficient
			WHERE pls_name != p_pls_name
		)
		AND ROWNUM = 1;

		IF v_wse_id IS NOT NULL THEN
			DBMS_OUTPUT.PUT_LINE('Retiring PLS would lead to ' || v_wse_id || ' (' || v_wse_name || ') not having language proficiencies.');
		ELSE
			--is pls primary in a project
			SELECT proj_name INTO v_primary_proj_name
			FROM assign
			WHERE pls_name = p_pls_name
			AND ROWNUM = 1;

			IF v_primary_proj_name IS NOT NULL THEN
				DBMS_OUTPUT.PUT_LINE('PLS is identified as primary in project assignment: ' || v_primary_proj_name);
			ELSE
				DBMS_OUTPUT.PUT_LINE('Success: PLS can be retired.');
			END IF;
		END IF;
	END IF;
EXCEPTION
	WHEN NO_DATA_FOUND THEN
		DBMS_OUTPUT.PUT_LINE('Success: PLS can be retired.');
END check_retire_pls;
/



--populate tables with mock data for debugging
INSERT INTO mts (id, mts_name, gender, salary, sup_id, wse_not_admin) VALUES (1, 'Worker A', 'm', 56000, NULL, 0);
INSERT INTO mts (id, mts_name, gender, salary, sup_id, wse_not_admin) VALUES (2, 'Worker B', 'f', 57000, 1, 1);
INSERT INTO mts (id, mts_name, gender, salary, sup_id, wse_not_admin) VALUES (3, 'Worker C', 'f', 58000, 1, 1);

INSERT INTO pls (pls_name, num_projects_requiring) VALUES ('Java', 2);
INSERT INTO pls (pls_name, num_projects_requiring) VALUES ('Python', 1);
INSERT INTO pls (pls_name, num_projects_requiring) VALUES ('JavaScript', 1);

INSERT INTO project (proj_name, start_date, completion_date, id) VALUES ('Project A', TO_DATE('2023-01-01', 'yyyy-mm-dd'), TO_DATE('2023-06-01', 'yyyy-mm-dd'), 1);
INSERT INTO project (proj_name, start_date, completion_date, id) VALUES ('Project B', TO_DATE('2023-02-01', 'yyyy-mm-dd'), TO_DATE('2023-07-01', 'yyyy-mm-dd'), 2);
INSERT INTO project (proj_name, start_date, completion_date, id) VALUES ('Project C', TO_DATE('2023-03-01', 'yyyy-mm-dd'), TO_DATE('2023-08-01', 'yyyy-mm-dd'), 3);
INSERT INTO project (proj_name, start_date, completion_date, id) VALUES ('Project D', TO_DATE('2023-04-01', 'yyyy-mm-dd'), TO_DATE('2023-09-01', 'yyyy-mm-dd'), 3);

INSERT INTO proficient (id, pls_name, degree_cert, date_degree_cert) VALUES (1, 'Java', 'Java Certification', TO_DATE('2022-01-01', 'yyyy-mm-dd'));
INSERT INTO proficient (id, pls_name, degree_cert, date_degree_cert) VALUES (2, 'Python', 'Python Certification', TO_DATE('2021-01-01', 'yyyy-mm-dd'));
INSERT INTO proficient (id, pls_name, degree_cert, date_degree_cert) VALUES (3, 'JavaScript', 'JS Cert', TO_DATE('2020-01-01', 'yyyy-mm-dd'));

INSERT INTO assign (id, proj_name, pls_name, assign_start_date, assign_end_date) VALUES (1, 'Project A', 'Java', TO_DATE('2023-01-01', 'yyyy-mm-dd'), TO_DATE('2023-06-01', 'yyyy-mm-dd'));
INSERT INTO assign (id, proj_name, pls_name, assign_start_date, assign_end_date) VALUES (2, 'Project B', 'Python', TO_DATE('2023-02-01', 'yyyy-mm-dd'), TO_DATE('2023-07-01', 'yyyy-mm-dd'));
INSERT INTO assign (id, proj_name, pls_name, assign_start_date, assign_end_date) VALUES (3, 'Project C', 'JavaScript', TO_DATE('2023-03-01', 'yyyy-mm-dd'), TO_DATE('2023-08-01', 'yyyy-mm-dd'));
INSERT INTO assign (id, proj_name, pls_name, assign_start_date, assign_end_date) VALUES (3, 'Project D', 'JavaScript', TO_DATE('2022-03-01', 'yyyy-mm-dd'), TO_DATE('2022-08-01', 'yyyy-mm-dd'));

INSERT INTO requires (proj_name, pls_name) VALUES ('Project A', 'Java');
INSERT INTO requires (proj_name, pls_name) VALUES ('Project B', 'Python');
INSERT INTO requires (proj_name, pls_name) VALUES ('Project C', 'JavaScript');
INSERT INTO requires (proj_name, pls_name) VALUES ('Project A', 'Python');
INSERT INTO requires (proj_name, pls_name) VALUES ('Project D', 'JavaScript');
 

SELECT
    proj_name,
    completion_date,
    completion_date - SYSDATE AS days_remaining
FROM
    project
WHERE
    SYSDATE BETWEEN start_date AND completion_date
ORDER BY
    completion_date ASC;

 
--find wses whose assignment to a project ends in 2022, but the project is due in 2023
SELECT
    m.id,
    m.mts_name
FROM
    mts m
JOIN assign a ON m.id = a.id
JOIN project p ON a.proj_name = p.proj_name
WHERE
    EXTRACT(YEAR FROM p.completion_date) = 2023
    AND EXTRACT(YEAR FROM a.assign_end_date) = 2022
ORDER BY
    m.id DESC;




  
--find all pls that have the max number of proficient wses and list them

SELECT
    pls_name,
    COUNT(id) AS num_wses
FROM
    proficient
GROUP BY
    pls_name
HAVING
    COUNT(id) = (
        SELECT
            MAX(wse_count)
        FROM (
            SELECT
                COUNT(id) AS wse_count
            FROM
                proficient
            GROUP BY
                pls_name
        )
    )
ORDER BY
    pls_name;

  

--find average salary of devs per pls, list descending


SELECT
    p.pls_name,
    AVG(m.salary) AS avg_salary
FROM
    pls p
LEFT JOIN assign a ON p.pls_name = a.pls_name
LEFT JOIN mts m ON a.id = m.id
LEFT JOIN project pj ON a.proj_name = pj.proj_name
WHERE
    pj.start_date >= TO_DATE('2020-01-01', 'yyyy-mm-dd')
    OR pj.start_date IS NULL
GROUP BY
    p.pls_name
ORDER BY
    avg_salary DESC;

  

--for each pls, display all supervisor-wse pairs where both are proficient in the pls

SELECT
    p.pls_name,
    m.id AS wse_id,
    m.mts_name AS wse_name,
    s.id AS supervisor_id,
    s.mts_name AS supervisor_name
FROM
    proficient pf_wse
JOIN mts m ON pf_wse.id = m.id
JOIN proficient pf_supervisor ON pf_wse.pls_name = pf_supervisor.pls_name
JOIN mts s ON pf_supervisor.id = m.sup_id
JOIN pls p ON p.pls_name = pf_wse.pls_name
ORDER BY
    p.pls_name,
    m.id,
    s.id;
