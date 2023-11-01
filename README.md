# Database Version Control (dbVcs)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Overview

The **Database Version Control** is a Java library designed to help developers manage and control the versioning of their database schema and data. It provides tools and utilities for tracking changes, applying updates, and maintaining a version history of your database.

Key features of this library include:

- **Schema Versioning:** Manage changes to your database schema over time.
- **Data Migration:** Apply data changes and transformations as part of the versioning process.
- **Version History:** Keep a record of all applied database updates.

This library is particularly useful for projects that require database schema and data changes to be tracked and applied systematically.

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Your Java Springboot project where you plan to use this library

### Installation

You can include this library in your Java project using Maven or Gradle. Add the following dependency to your project's build file:

#### Maven

```xml
		<dependency>
			<groupId>sdk.db.vcs</groupId>
			<artifactId>db-vcs</artifactId>
			<version>1.0-SNAPSHOT</version>
		</dependency>
```

#### Local Installation
Clone this Repo and run command 

```mvn clean install``` (this would create a Jar in your Local maven repository)

and now use above maven dependency and change version as per the need

#### Java
 Enabling Version Control using Annotation
```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import sdk.db.vcs.annotations.EnableDbVcs;

@SpringBootApplication
@EnableDbVcs
public class DemoApplication {
	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
```

#### application.properties
 Enabling version control with properties

```
spring.datasource.username=root
spring.datasource.password=xxxxx
spring.datasource.url=jdbc:mysql://localhost:3306/student_db
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
db.vcs.enabled=true //Optional
```

#### Download Necessary Connector 

Download necessary connector for your RDBMS
Dependency Mysql 8 connector

```
		<dependency>
			<groupId>mysql</groupId>
			<artifactId>mysql-connector-java</artifactId>
			<version>8.0.33</version>
		</dependency>

```

Similarly for other RDBMS, download necessay connectors

#### Usage

Create Sql file inside ```resources/db-migration``` using following way

FileName : ```V<version>__fileDescripton.sql ```

Example : ```V1___createStudents.sql ```

Content : ```CREATE TABLE Student (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    date_of_birth DATE,
    gender ENUM('Male', 'Female', 'Other'),
    email VARCHAR(100) UNIQUE,
    phone_number VARCHAR(15),
    address VARCHAR(255)
);```
