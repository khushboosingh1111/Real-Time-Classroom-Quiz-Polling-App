package com.quiz.config;

import com.quiz.model.Question;
import com.quiz.model.Quiz;
import com.quiz.repository.QuestionRepository;
import com.quiz.repository.QuizRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(QuizRepository quizRepo, QuestionRepository questionRepo) {
        return args -> {
            // Quiz 1: Tech Trivia
            Quiz q1 = new Quiz();
            q1.setTitle("Tech Trivia Blitz");
            q1.setDescription("Test your tech knowledge in this lightning-fast quiz!");
            q1.setHostName("QuizMaster");
            q1.setJoinCode("TECH42");
            quizRepo.save(q1);

            questionRepo.saveAll(List.of(
                createQ(q1.getId(), "What does HTML stand for?",
                    "Hyper Text Markup Language", "High Tech Modern Language",
                    "Hyper Transfer Markup Language", "Home Tool Markup Language",
                    "A", 15, 1000, "Web", "EASY"),
                createQ(q1.getId(), "Which company created Java?",
                    "Microsoft", "Sun Microsystems", "Apple", "Google",
                    "B", 15, 1000, "Programming", "EASY"),
                createQ(q1.getId(), "What is the time complexity of binary search?",
                    "O(n)", "O(n²)", "O(log n)", "O(1)",
                    "C", 20, 1500, "Algorithms", "MEDIUM"),
                createQ(q1.getId(), "Which protocol is used for secure web browsing?",
                    "HTTP", "FTP", "HTTPS", "SMTP",
                    "C", 15, 1000, "Networking", "EASY"),
                createQ(q1.getId(), "What does SQL stand for?",
                    "Structured Query Language", "Simple Question Language",
                    "Standard Query Logic", "Sequential Query Language",
                    "A", 15, 1000, "Database", "EASY"),
                createQ(q1.getId(), "Which data structure uses LIFO?",
                    "Queue", "Stack", "Array", "LinkedList",
                    "B", 15, 1200, "Data Structures", "MEDIUM"),
                createQ(q1.getId(), "What is React primarily used for?",
                    "Backend APIs", "Database management",
                    "Building user interfaces", "Operating systems",
                    "C", 15, 1000, "Frontend", "EASY"),
                createQ(q1.getId(), "Which sorting algorithm has best average case O(n log n)?",
                    "Bubble Sort", "Merge Sort", "Selection Sort", "Insertion Sort",
                    "B", 20, 1500, "Algorithms", "MEDIUM"),
                createQ(q1.getId(), "What is Docker used for?",
                    "Version control", "Containerization",
                    "Text editing", "Email",
                    "B", 15, 1000, "DevOps", "EASY"),
                createQ(q1.getId(), "What does API stand for?",
                    "Application Programming Interface", "Advanced Program Integration",
                    "Automated Process Interaction", "Application Process Integration",
                    "A", 15, 1000, "General", "EASY")
            ));

            // Quiz 2: Science Quiz
            Quiz q2 = new Quiz();
            q2.setTitle("Science Showdown");
            q2.setDescription("Battle it out with science questions!");
            q2.setHostName("ScienceHost");
            q2.setJoinCode("SCI999");
            quizRepo.save(q2);

            questionRepo.saveAll(List.of(
                createQ(q2.getId(), "What is the chemical symbol for water?",
                    "H2O", "CO2", "NaCl", "O2",
                    "A", 15, 1000, "Chemistry", "EASY"),
                createQ(q2.getId(), "What planet is known as the Red Planet?",
                    "Venus", "Jupiter", "Mars", "Saturn",
                    "C", 15, 1000, "Astronomy", "EASY"),
                createQ(q2.getId(), "What is the speed of light (approx)?",
                    "300,000 km/s", "150,000 km/s", "500,000 km/s", "100,000 km/s",
                    "A", 20, 1500, "Physics", "MEDIUM"),
                createQ(q2.getId(), "What is the powerhouse of the cell?",
                    "Nucleus", "Ribosome", "Mitochondria", "Golgi body",
                    "C", 15, 1000, "Biology", "EASY"),
                createQ(q2.getId(), "What gas do plants absorb from the atmosphere?",
                    "Oxygen", "Nitrogen", "Carbon Dioxide", "Hydrogen",
                    "C", 15, 1000, "Biology", "EASY")
            ));

            System.out.println("✅ Seeded 2 quizzes with 15 questions total");
            System.out.println("📌 Join codes: TECH42, SCI999");
        };
    }

    private Question createQ(Long quizId, String text, String a, String b, String c, String d,
                              String correct, int time, int points, String category, String diff) {
        Question q = new Question();
        q.setQuizId(quizId);
        q.setQuestionText(text);
        q.setOptionA(a);
        q.setOptionB(b);
        q.setOptionC(c);
        q.setOptionD(d);
        q.setCorrectOption(correct);
        q.setTimeLimit(time);
        q.setPoints(points);
        q.setCategory(category);
        q.setDifficulty(diff);
        return q;
    }
}
