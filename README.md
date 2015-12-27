# sqls

sqls is simple SQL client for programmers and engineers.
It's goal is to make running SQL queries easier.

## Screenshots

![SQLs Conn List Window](screenshots/sqls-conn-list.png?raw=true "SQLs Conn List Window")
![SQLs Worksheet Window](screenshots/sqls-worksheet.png?raw=true "SQLs Worksheet Window")

## Installation

1. Make sure you've got fairly recent Java Runtime Environment installed (you can get one at http://java.oracle.com/),
2. Download sqls.jar (Windows, Linux) or SQLs.app (OS X) from https://github.com/mpietrzak/sqls/releases,
3. Put it where you like.

## Running

### Windows and Linux

Double click on sqls.jar.
If .jar files on your system are not associated with Java, then you might have to choose "Open with...".
You can also run it by hand by executing `java -jar sqls.jar`.

### OS X

Double click SQLs app.

## Usage

Put driver jars into ~/.sqls/lib firectory to connect to other DB types. Only PostgreSQL and SQLite drivers are bundled.
To connect to Oracle: put ojdbc7.jar, xdb6.jar and xmlparserv2.jar (last two only needed for XML support).

Add connection, open worksheet, write SQL statements, separate them by empty lines.

Press Ctrl-Enter to run statement under cursor.

Press Ctrl-P (Windows, Linux) or Cmd-P (OS X) to show command palette.

## License

Copyright © 2014 Maciej Pietrzak

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
