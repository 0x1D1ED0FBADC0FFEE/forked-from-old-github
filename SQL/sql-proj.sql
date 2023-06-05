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

create table mts (
	id number(3) primary key,
	mts_name char(20) not null,
	gender char(1) check (gender in ('m', 'f')),
	salary number(10),
	sup_id number(3),
	wse_not_admin number(1) check (wse_not_admin in (0, 1)),
	constraint fk_sup foreign key (sup_id) references mts(id)
);

create table pls (
	pls_name char(20) primary key,
	num_projects_requiring number(10) not null
);

create table project (
	proj_name char(20) primary key,
	start_date date not null,
	completion_date date,
	id number(3) not null,
	constraint fk_mts foreign key (id) references mts(id)
);

create table proficient (
	id number(3) not null,
	pls_name char(20) not null,
	degree_cert char(20) not null,
	date_degree_cert date,
	constraint fk_mts_prof foreign key (id) references mts(id),
	constraint fk_pls_prof foreign key (pls_name) references pls(pls_name)
);

create table assign (
	id number(3) not null,
	proj_name char(20) not null,
	pls_name char(20) not null,
	assign_start_date date not null,
	assign_end_date date,
	constraint fk_mts_assign foreign key (id) references mts(id),
	constraint fk_proj_assign foreign key (proj_name) references project(proj_name),
	constraint fk_pls_assign foreign key (pls_name) references pls(pls_name)
);

create table requires (
	proj_name char(20) not null,
	pls_name char(20) not null,
	constraint fk_proj_req foreign key (proj_name) references project(proj_name),
	constraint fk_pls_req foreign key (pls_name) references pls(pls_name)
);

--add constraints
alter table pls add (
	constraint num_proj_req check (num_projects_requiring = (
		select count(*) from requires where requires.pls_name = pls.pls_name
	))
);

--add trigger
create proficient_check
before insert or update on assign
for each row
declare
	cnt number;
begin
	select count(*) into cnt from proficient
	where proficient.id = :new.id and proficient.pls_name = :new.pls_name;
		if cnt = 0 then
			raise_application_error(-20500, 'Dev not proficient in required pls.');
		end if;
end;


--procedures first so the database can be populated


--create new mts, id sequence starts at 10 so 0 through 9 can be used for debugging
create sequence mts_id_seq
	start with 10
	increment by 1
	nocache;

create procedure hire_mts(
	p_name in char,
	p_gender in char,
	p_salary in number,
	p_supervisor_id in number
) as
begin
	insert into mts (
		id,
		mts_name,
		gender,
		salary,
		sup_id,
		wse_not_admin
)
  values (
	mts_id_seq.nextval,
	p_name,
	p_gender,
	p_salary,
	p_supervisor_id,
	1
  );
  commit;
end hire_mts;
/

--add pls to db
create procedure add_pls(
  p_pls_name in char
) as
begin
  insert into pls (
    pls_name,
    num_projects_requiring
  )
  values (
    p_pls_name,
    0
  );
  commit;
end add_pls;
/

--add a project requirement to a project
create procedure add_project_req(
	p_proj_name in char,
	p_pls_name in char
) as
	v_project_count number;
begin
  --does project exist
	select count(*) into v_project_count
	from project
	where proj_name = p_proj_name;

  --if not, insert
  if v_project_count = 0 then
    insert into project (
      proj_name,
      start_date,
      id
    )
    values (
      p_proj_name,
      sysdate,
      null
    );
  end if;

  --update pls requirements
  insert into requires (
	proj_name,
    pls_name
  )
  values (
    p_proj_name,
    p_pls_name
  );

  commit;
end add_project_req;
/


--see if pls can be retired
create procedure check_retire_pls(
	p_pls_name in char
) as
	v_project_name char(20);
	v_wse_id number;
	v_wse_name char(20);
	v_primary_proj_name char(20);
begin
	--is pls required by any project
	select proj_name into v_project_name
	from requires
	where pls_name = p_pls_name
	and rownum = 1;

  if v_project_name is not null then
    dbms_output.put_line('pls required by project: ' || v_project_name);
  else
    --if retiring would leave a developer without proficiencies
	select id, mts_name into v_wse_id, v_wse_name
    from mts
    where id not in (
      select id
      from proficient
      where pls_name != p_pls_name
    )
    and rownum = 1;

    if v_wse_id is not null then
      dbms_output.put_line('retiring pls would lead to ' || v_wse_id || ' (' || v_wse_name || ') not having language proficiencies.');
    else
      --is pls primary in a project
      select proj_name into v_primary_proj_name
      from assign
      where pls_name = p_pls_name
      and rownum = 1;

      if v_primary_proj_name is not null then
        dbms_output.put_line('PLS is identified as primary in project assignment: ' || v_primary_proj_name);
      else
        dbms_output.put_line('Success: PLS can be retired.');
      end if;
    end if;
  end if;
exception
  when no_data_found then
    dbms_output.put_line('Success: PLS can be retired.');
end check_retire_pls;
/



--populate tables with mock data for debugging
insert into mts (id, mts_name, gender, salary, sup_id, wse_not_admin) values (1, 'Worker A', 'm', 56000, null, 0);
insert into mts (id, mts_name, gender, salary, sup_id, wse_not_admin) values (2, 'Worker B', 'f', 57000, 1, 1);
insert into mts (id, mts_name, gender, salary, sup_id, wse_not_admin) values (3, 'Worker C', 'f', 58000, 1, 1);

insert into pls (pls_name, num_projects_requiring) values ('Java', 2);
insert into pls (pls_name, num_projects_requiring) values ('Python', 1);
insert into pls (pls_name, num_projects_requiring) values ('JavaScript', 1);

insert into project (proj_name, start_date, completion_date, id) values ('Project A', to_date('2023-01-01', 'yyyy-mm-dd'), to_date('2023-06-01', 'yyyy-mm-dd'), 1);
insert into project (proj_name, start_date, completion_date, id) values ('Project B', to_date('2023-02-01', 'yyyy-mm-dd'), to_date('2023-07-01', 'yyyy-mm-dd'), 2);
insert into project (proj_name, start_date, completion_date, id) values ('Project C', to_date('2023-03-01', 'yyyy-mm-dd'), to_date('2023-08-01', 'yyyy-mm-dd'), 3);
insert into project (proj_name, start_date, completion_date, id) values ('Project D', to_date('2023-04-01', 'yyyy-mm-dd'), to_date('2023-09-01', 'yyyy-mm-dd'), 3);

insert into proficient (id, pls_name, degree_cert, date_degree_cert) values (1, 'Java', 'Java Certification', to_date('2022-01-01', 'yyyy-mm-dd'));
insert into proficient (id, pls_name, degree_cert, date_degree_cert) values (2, 'Python', 'Python Certification', to_date('2021-01-01', 'yyyy-mm-dd'));
insert into proficient (id, pls_name, degree_cert, date_degree_cert) values (3, 'JavaScript', 'JS Cert', to_date('2020-01-01', 'yyyy-mm-dd'));

insert into assign (id, proj_name, pls_name, assign_start_date, assign_end_date) values (1, 'Project A', 'Java', to_date('2023-01-01', 'yyyy-mm-dd'), to_date('2023-06-01', 'yyyy-mm-dd'));
insert into assign (id, proj_name, pls_name, assign_start_date, assign_end_date) values (2, 'Project B', 'Python', to_date('2023-02-01', 'yyyy-mm-dd'), to_date('2023-07-01', 'yyyy-mm-dd'));
insert into assign (id, proj_name, pls_name, assign_start_date, assign_end_date) values (3, 'Project C', 'JavaScript', to_date('2023-03-01', 'yyyy-mm-dd'), to_date('2023-08-01', 'yyyy-mm-dd'));
insert into assign (id, proj_name, pls_name, assign_start_date, assign_end_date) values (3, 'Project D', 'JavaScript', to_date('2022-03-01', 'yyyy-mm-dd'), to_date('2022-08-01', 'yyyy-mm-dd'));

insert into requires (proj_name, pls_name) values ('Project A', 'Java');
insert into requires (proj_name, pls_name) values ('Project B', 'Python');
insert into requires (proj_name, pls_name) values ('Project C', 'JavaScript');
insert into requires (proj_name, pls_name) values ('Project A', 'Python');
insert into requires (proj_name, pls_name) values ('Project D', 'JavaScript');


--find days remaining for projects whose start and due date include the current date in the interval between them
select
    proj_name,
    completion_date,
    completion_date - sysdate as days_remaining
from
	project
where
	sysdate between start_date and completion_date
order by
	completion_date asc;
 
 
--find wses whose assignment to a project ends in 2022, but the project is due in 2023
select
	m.id,
	m.mts_name
from
	mts m
	join assign a on m.id = a.id
	join project p on a.proj_name = p.proj_name
where
	extract(year from p.completion_date) = 2023
	and extract(year from a.assign_end_date) = 2022
order by
	m.id desc;
  
  
  
--find all pls that have the max number of proficient wses and list them
select
	pls_name,
	count(id) as num_wses
from
	proficient
group by
	pls_name
having
	count(id) = (
		select
			max(wse_count)
		from (
		select
			count(id) as wse_count
		from
			proficient
		group by
			pls_name
    )
  )
order by
  pls_name;
  

--find average salary of devs per pls, list descending
select
	p.pls_name,
	avg(m.salary) as avg_salary
from
	pls p
	left join assign a on p.pls_name = a.pls_name
	left join mts m on a.id = m.id
	left join project pj on a.proj_name = pj.proj_name
where
	pj.start_date >= to_date('2020-01-01', 'yyyy-mm-dd')
	or pj.start_date is null
group by
	p.pls_name
order by
	avg_salary desc;
  

--for each pls, display all supervisor-wse pairs where both are proficient in the pls
select
	p.pls_name,
	m.id as wse_id,
	m.mts_name as wse_name,
	s.id as supervisor_id,
	s.mts_name as supervisor_name
from
	proficient pf_wse
	join mts m on pf_wse.id = m.id
	join proficient pf_supervisor on pf_wse.pls_name = pf_supervisor.pls_name
	join mts s on pf_supervisor.id = m.sup_id
	join pls p on p.pls_name = pf_wse.pls_name
order by
	p.pls_name,
	m.id,
	s.id;



