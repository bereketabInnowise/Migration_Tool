-- Users table (OneToOne with Profiles)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE
);

-- Profiles table (OneToOne with Users)
CREATE TABLE  profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE,
    email VARCHAR(100) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Orders table (OneToMany with Users)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    order_date DATE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Students table (ManyToMany with Courses)
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    student_name VARCHAR(50) NOT NULL
);

-- Courses table
CREATE TABLE courses (
    id BIGSERIAL PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL
);

-- Junction table for Students-Courses (ManyToMany)
CREATE TABLE student_courses (
    student_id BIGINT,
    course_id BIGINT,
    PRIMARY KEY (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES students(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_profiles_user_id ON profiles(user_id);


-- Insert 50 Users
INSERT INTO users (username) VALUES
('user1'), ('user2'), ('user3'), ('user4'), ('user5'),
('user6'), ('user7'), ('user8'), ('user9'), ('user10'),
('user11'), ('user12'), ('user13'), ('user14'), ('user15'),
('user16'), ('user17'), ('user18'), ('user19'), ('user20'),
('user21'), ('user22'), ('user23'), ('user24'), ('user25'),
('user26'), ('user27'), ('user28'), ('user29'), ('user30'),
('user31'), ('user32'), ('user33'), ('user34'), ('user35'),
('user36'), ('user37'), ('user38'), ('user39'), ('user40'),
('user41'), ('user42'), ('user43'), ('user44'), ('user45'),
('user46'), ('user47'), ('user48'), ('user49'), ('user50');

-- Insert 50 Profiles (linked to Users)
INSERT INTO profiles (user_id, email)
SELECT id, username || '@example.com' FROM users;

-- Insert 50 Orders (linked to Users)
INSERT INTO orders (user_id, order_date)
SELECT id, CURRENT_DATE - INTERVAL '1 day' * id FROM users;

-- Insert 10 Students
INSERT INTO students (student_name) VALUES
('Alice'), ('Bob'), ('Charlie'), ('David'), ('Eve'),
('Frank'), ('Grace'), ('Hannah'), ('Ivy'), ('Jack');

-- Insert 5 Courses
INSERT INTO courses (course_name) VALUES
('Math'), ('Physics'), ('Chemistry'), ('Biology'), ('History');

-- Insert 25 Student-Courses (ManyToMany links)
INSERT INTO student_courses (student_id, course_id) VALUES
(1, 1), (1, 2), (2, 1), (2, 3), (3, 2),
(3, 4), (4, 3), (4, 5), (5, 1), (5, 4),
(6, 2), (6, 5), (7, 3), (7, 1), (8, 4),
(8, 2), (9, 5), (9, 3), (10, 1), (10, 4),
(1, 3), (2, 4), (3, 5), (4, 1), (5, 2);