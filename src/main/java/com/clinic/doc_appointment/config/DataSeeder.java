package com.clinic.doc_appointment.config;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.enums.Gender;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Development fixture data: five doctors, five patients, a week of slots and ten appointments.
 *
 * <p><strong>{@code @Profile("dev")} is a safety control, not a convenience.</strong> This class
 * previously ran on any startup where the doctors table happened to be empty — production
 * included — creating five prescriber accounts whose password is the string {@code password123},
 * along with realistic-looking clinical notes. An empty table is a completely normal state for a
 * fresh production database, so nothing stood between a first deploy and those accounts existing.
 *
 * <p>{@code @Transactional} matters for a second reason: seeding is one unit of work. Half-seeded
 * data used to be permanent, because the {@code count() > 0} guard then skipped the repair forever
 * on every subsequent boot.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Annotated on the overridden method rather than a helper, so Spring Boot's external call to
     * {@code run} passes through the transactional proxy. A private helper would be self-invoked
     * and silently unadvised.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (doctorRepository.count() > 0) {
            log.info("Database already seeded. Skipping.");
            return;
        }

        log.info("Seeding development fixture data...");

        // ════════════════════════════════════════════════
        // STEP 1: CREATE DOCTORS
        // ════════════════════════════════════════════════
        Doctor doctor1 = doctorRepository.save(new Doctor()
                .setFirstName("Anjali")
                .setLastName("Sharma")
                .setEmail("anjali.sharma@clinic.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9876543210")
                .setSpecialization(Specialization.CARDIOLOGIST)
                .setQualification("MBBS, MD (Cardiology), DM (Interventional Cardiology)")
                .setExperienceYears(12)
                .setConsultationFee(new BigDecimal("900.00"))
                .setAbout("Senior Cardiologist specializing in interventional cardiology and heart failure management. Former consultant at AIIMS Delhi.")
                .setIsActive(true));

        Doctor doctor2 = doctorRepository.save(new Doctor()
                .setFirstName("Rahul")
                .setLastName("Verma")
                .setEmail("rahul.verma@clinic.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9123456780")
                .setSpecialization(Specialization.DERMATOLOGIST)
                .setQualification("MBBS, DVD (Dermatology), Fellowship in Cosmetology")
                .setExperienceYears(8)
                .setConsultationFee(new BigDecimal("700.00"))
                .setAbout("Expert in medical and cosmetic dermatology, treating acne, eczema, psoriasis, and performing laser skin treatments.")
                .setIsActive(true));

        Doctor doctor3 = doctorRepository.save(new Doctor()
                .setFirstName("Preethi")
                .setLastName("Nair")
                .setEmail("preethi.nair@clinic.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9988776655")
                .setSpecialization(Specialization.PEDIATRICIAN)
                .setQualification("MBBS, MD (Pediatrics), Fellowship in Neonatology")
                .setExperienceYears(15)
                .setConsultationFee(new BigDecimal("600.00"))
                .setAbout("Experienced Pediatrician with 15 years in child health care, newborn care, and adolescent medicine.")
                .setIsActive(true));

        Doctor doctor4 = doctorRepository.save(new Doctor()
                .setFirstName("Arjun")
                .setLastName("Kapoor")
                .setEmail("arjun.kapoor@clinic.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9001122334")
                .setSpecialization(Specialization.ORTHOPEDIC_SURGEON)
                .setQualification("MBBS, MS (Orthopedics), Fellowship in Joint Replacement")
                .setExperienceYears(10)
                .setConsultationFee(new BigDecimal("1000.00"))
                .setAbout("Specialist in knee and hip replacement surgeries, sports injuries, and spine disorders.")
                .setIsActive(true));

        Doctor doctor5 = doctorRepository.save(new Doctor()
                .setFirstName("Sunita")
                .setLastName("Rao")
                .setEmail("sunita.rao@clinic.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9445566778")
                .setSpecialization(Specialization.GENERAL_PRACTITIONER)
                .setQualification("MBBS, PGDM (Family Medicine)")
                .setExperienceYears(5)
                .setConsultationFee(new BigDecimal("400.00"))
                .setAbout("General Practitioner providing comprehensive primary care for patients of all ages.")
                .setIsActive(true));

        // ════════════════════════════════════════════════
        // STEP 2: CREATE PATIENTS
        // ════════════════════════════════════════════════
        Patient patient1 = patientRepository.save(new Patient()
                .setFirstName("Priya")
                .setLastName("Mehta")
                .setEmail("priya.mehta@gmail.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9001234567")
                .setGender(Gender.FEMALE)
                .setDateOfBirth(LocalDate.of(1992, 6, 15))
                .setAddress("12, MG Road, Bangalore - 560001"));

        Patient patient2 = patientRepository.save(new Patient()
                .setFirstName("Amit")
                .setLastName("Singh")
                .setEmail("amit.singh@gmail.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9009876543")
                .setGender(Gender.MALE)
                .setDateOfBirth(LocalDate.of(1985, 3, 22))
                .setAddress("45, Nehru Nagar, Delhi - 110001"));

        Patient patient3 = patientRepository.save(new Patient()
                .setFirstName("Kavya")
                .setLastName("Reddy")
                .setEmail("kavya.reddy@gmail.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9876001234")
                .setGender(Gender.FEMALE)
                .setDateOfBirth(LocalDate.of(1998, 11, 5))
                .setAddress("7, Banjara Hills, Hyderabad - 500034"));

        Patient patient4 = patientRepository.save(new Patient()
                .setFirstName("Rohit")
                .setLastName("Joshi")
                .setEmail("rohit.joshi@gmail.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9321654987")
                .setGender(Gender.MALE)
                .setDateOfBirth(LocalDate.of(1975, 8, 18))
                .setAddress("23, Shivaji Nagar, Pune - 411005"));

        Patient patient5 = patientRepository.save(new Patient()
                .setFirstName("Meena")
                .setLastName("Iyer")
                .setEmail("meena.iyer@gmail.com")
                .setPassword(passwordEncoder.encode("password123"))
                .setCountryCode("+91")
                .setPhoneNumber("9654123098")
                .setGender(Gender.FEMALE)
                .setDateOfBirth(LocalDate.of(2010, 2, 28))
                .setAddress("56, Anna Nagar, Chennai - 600040"));

        // ════════════════════════════════════════════════
        // STEP 3: CREATE SLOTS
        // Past (2 days ago), Yesterday, Today, Tomorrow, Day After
        // Each doctor gets multiple slots per day
        // ════════════════════════════════════════════════
        LocalDate today = LocalDate.now();

        List<DoctorAvailability> allSlots = new ArrayList<>();

        // Helper: creates a single slot
        // Doctor1 (Cardiologist) - slots for 5 days
        for (int offset = -2; offset <= 2; offset++) {
            LocalDate date = today.plusDays(offset);
            allSlots.add(makeSlot(doctor1, date, LocalTime.of(9, 0), LocalTime.of(9, 30), 30));
            allSlots.add(makeSlot(doctor1, date, LocalTime.of(9, 30), LocalTime.of(10, 0), 30));
            allSlots.add(makeSlot(doctor1, date, LocalTime.of(10, 0), LocalTime.of(10, 30), 30));
            allSlots.add(makeSlot(doctor1, date, LocalTime.of(11, 0), LocalTime.of(11, 30), 30));
            allSlots.add(makeSlot(doctor1, date, LocalTime.of(14, 0), LocalTime.of(14, 30), 30));
            allSlots.add(makeSlot(doctor1, date, LocalTime.of(15, 0), LocalTime.of(15, 30), 30));
        }

        // Doctor2 (Dermatologist) - slots for 5 days
        for (int offset = -2; offset <= 2; offset++) {
            LocalDate date = today.plusDays(offset);
            allSlots.add(makeSlot(doctor2, date, LocalTime.of(10, 0), LocalTime.of(10, 30), 30));
            allSlots.add(makeSlot(doctor2, date, LocalTime.of(10, 30), LocalTime.of(11, 0), 30));
            allSlots.add(makeSlot(doctor2, date, LocalTime.of(11, 0), LocalTime.of(11, 30), 30));
            allSlots.add(makeSlot(doctor2, date, LocalTime.of(16, 0), LocalTime.of(16, 30), 30));
        }

        // Doctor3 (Pediatrician) - slots for 5 days
        for (int offset = -2; offset <= 2; offset++) {
            LocalDate date = today.plusDays(offset);
            allSlots.add(makeSlot(doctor3, date, LocalTime.of(8, 0), LocalTime.of(8, 30), 30));
            allSlots.add(makeSlot(doctor3, date, LocalTime.of(8, 30), LocalTime.of(9, 0), 30));
            allSlots.add(makeSlot(doctor3, date, LocalTime.of(17, 0), LocalTime.of(17, 30), 30));
        }

        // Doctor4 (Orthopedic) - 60-min slots
        for (int offset = -1; offset <= 2; offset++) {
            LocalDate date = today.plusDays(offset);
            allSlots.add(makeSlot(doctor4, date, LocalTime.of(9, 0), LocalTime.of(10, 0), 60));
            allSlots.add(makeSlot(doctor4, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 60));
            allSlots.add(makeSlot(doctor4, date, LocalTime.of(14, 0), LocalTime.of(15, 0), 60));
        }

        // Doctor5 (GP) - 15-min slots
        for (int offset = 0; offset <= 2; offset++) {
            LocalDate date = today.plusDays(offset);
            allSlots.add(makeSlot(doctor5, date, LocalTime.of(9, 0), LocalTime.of(9, 15), 15));
            allSlots.add(makeSlot(doctor5, date, LocalTime.of(9, 15), LocalTime.of(9, 30), 15));
            allSlots.add(makeSlot(doctor5, date, LocalTime.of(9, 30), LocalTime.of(9, 45), 15));
            allSlots.add(makeSlot(doctor5, date, LocalTime.of(9, 45), LocalTime.of(10, 0), 15));
            allSlots.add(makeSlot(doctor5, date, LocalTime.of(10, 0), LocalTime.of(10, 15), 15));
        }

        List<DoctorAvailability> savedSlots = slotRepository.saveAll(allSlots);
        log.info("  {} Slots saved", allSlots.size());

        // ════════════════════════════════════════════════
        // STEP 4: CREATE APPOINTMENTS (pick specific saved slots)
        // Reload slots from DB to get generated IDs
        // ════════════════════════════════════════════════

        List<Appointment> appointments = new ArrayList<>();

        // --- COMPLETED (2 days ago) ---
        DoctorAvailability s1 = pickSlot(savedSlots, doctor1, today.minusDays(2), LocalTime.of(9, 0));
        s1.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0001")
                .setPatient(patient1)
                .setDoctor(doctor1)
                .setSlot(s1)
                .setStatus(AppointmentStatus.COMPLETED)
                .setReasonForVisit("Chest pain and breathlessness during exercise")
                .setNotes("ECG normal. Advised stress test. Prescribed beta-blockers for 1 month."));

        DoctorAvailability s2 = pickSlot(savedSlots, doctor1, today.minusDays(2), LocalTime.of(9, 30));
        s2.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0002")
                .setPatient(patient2)
                .setDoctor(doctor1)
                .setSlot(s2)
                .setStatus(AppointmentStatus.COMPLETED)
                .setReasonForVisit("Routine cardiac checkup")
                .setNotes("BP: 130/85. Advised dietary changes and daily walks."));

        // --- NO_SHOW (yesterday) ---
        DoctorAvailability s3 = pickSlot(savedSlots, doctor1, today.minusDays(1), LocalTime.of(9, 0));
        s3.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0003")
                .setPatient(patient3)
                .setDoctor(doctor1)
                .setSlot(s3)
                .setStatus(AppointmentStatus.NO_SHOW)
                .setReasonForVisit("Follow-up for hypertension medication")
                .setNotes("Patient did not show up. Follow-up call scheduled."));

        // --- CANCELLED (yesterday, doctor2) ---
        DoctorAvailability s4 = pickSlot(savedSlots, doctor2, today.minusDays(2), LocalTime.of(10, 0));
        s4.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0004")
                .setPatient(patient4)
                .setDoctor(doctor2)
                .setSlot(s4)
                .setStatus(AppointmentStatus.CANCELLED)
                .setReasonForVisit("Skin rash on forearms")
                .setNotes("Cancelled by patient due to personal reasons."));

        // --- CONFIRMED (today, doctor1) ---
        DoctorAvailability s5 = pickSlot(savedSlots, doctor1, today, LocalTime.of(9, 0));
        s5.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0005")
                .setPatient(patient5)
                .setDoctor(doctor1)
                .setSlot(s5)
                .setStatus(AppointmentStatus.CONFIRMED)
                .setReasonForVisit("Heart palpitations since last 2 weeks")
                .setNotes("Holter monitor advised. Blood test ordered."));

        // --- PENDING (today, doctor2) ---
        DoctorAvailability s6 = pickSlot(savedSlots, doctor2, today, LocalTime.of(10, 0));
        s6.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0006")
                .setPatient(patient1)
                .setDoctor(doctor2)
                .setSlot(s6)
                .setStatus(AppointmentStatus.PENDING)
                .setReasonForVisit("Acne breakout and oily skin")
                .setNotes(null));

        // --- CONFIRMED (today, doctor3 Pediatrician) ---
        DoctorAvailability s7 = pickSlot(savedSlots, doctor3, today, LocalTime.of(8, 0));
        s7.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0007")
                .setPatient(patient5)
                .setDoctor(doctor3)
                .setSlot(s7)
                .setStatus(AppointmentStatus.CONFIRMED)
                .setReasonForVisit("Child annual growth checkup")
                .setNotes("Height and weight charts normal. Vaccines up to date."));

        // --- PENDING (tomorrow, doctor4 Orthopedic) ---
        DoctorAvailability s8 = pickSlot(savedSlots, doctor4, today.plusDays(1), LocalTime.of(9, 0));
        s8.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0008")
                .setPatient(patient2)
                .setDoctor(doctor4)
                .setSlot(s8)
                .setStatus(AppointmentStatus.PENDING)
                .setReasonForVisit("Knee pain while climbing stairs")
                .setNotes(null));

        // --- PENDING (tomorrow, doctor5 GP) ---
        DoctorAvailability s9 = pickSlot(savedSlots, doctor5, today.plusDays(1), LocalTime.of(9, 0));
        s9.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0009")
                .setPatient(patient3)
                .setDoctor(doctor5)
                .setSlot(s9)
                .setStatus(AppointmentStatus.PENDING)
                .setReasonForVisit("Fever and body ache for 3 days")
                .setNotes(null));

        // --- CONFIRMED (day after tomorrow, doctor1) ---
        DoctorAvailability s10 = pickSlot(savedSlots, doctor1, today.plusDays(2), LocalTime.of(9, 0));
        s10.setIsAvailable(false);
        appointments.add(new Appointment()
                .setAppointmentNumber("APT-2024-0010")
                .setPatient(patient4)
                .setDoctor(doctor1)
                .setSlot(s10)
                .setStatus(AppointmentStatus.CONFIRMED)
                .setReasonForVisit("Post-surgery cardiac review")
                .setNotes("Review of CABG surgery done 3 months ago. Echo scheduled."));

        // Save all booked slots
        slotRepository.saveAll(List.of(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10));
        appointmentRepository.saveAll(appointments);

        log.info("  5 Doctors | 5 Patients | {} Slots | {} Appointments seeded successfully!",
                allSlots.size(), appointments.size());
        log.info("");
        log.info("  Doctors:");
        log.info("     - {} {} [{}] → {}", doctor1.getFirstName(), doctor1.getLastName(), doctor1.getSpecialization(), doctor1.getDoctorId());
        log.info("     - {} {} [{}] → {}", doctor2.getFirstName(), doctor2.getLastName(), doctor2.getSpecialization(), doctor2.getDoctorId());
        log.info("     - {} {} [{}] → {}", doctor3.getFirstName(), doctor3.getLastName(), doctor3.getSpecialization(), doctor3.getDoctorId());
        log.info("     - {} {} [{}] → {}", doctor4.getFirstName(), doctor4.getLastName(), doctor4.getSpecialization(), doctor4.getDoctorId());
        log.info("     - {} {} [{}] → {}", doctor5.getFirstName(), doctor5.getLastName(), doctor5.getSpecialization(), doctor5.getDoctorId());
        log.info("");
        log.info("  Patients:");
        log.info("     - {} {} → {}", patient1.getFirstName(), patient1.getLastName(), patient1.getPatientId());
        log.info("     - {} {} → {}", patient2.getFirstName(), patient2.getLastName(), patient2.getPatientId());
        log.info("     - {} {} → {}", patient3.getFirstName(), patient3.getLastName(), patient3.getPatientId());
        log.info("     - {} {} → {}", patient4.getFirstName(), patient4.getLastName(), patient4.getPatientId());
        log.info("     - {} {} → {}", patient5.getFirstName(), patient5.getLastName(), patient5.getPatientId());
    }

    /**
     * Finds a seeded slot by what it <em>is</em>, not by where it landed in a list.
     *
     * <p>The appointments below used to select slots as {@code findAll().get(30)}, {@code .get(65)},
     * {@code .get(77)} — positional indexes into a query with no ORDER BY. Three of those comments
     * had already drifted from the row they actually selected (index 34 was yesterday, not today),
     * which is the failure mode this replaces: editing any doctor's slot list silently re-pointed
     * later appointments at a different doctor's calendar. Failing loudly beats seeding nonsense.
     */
    private static DoctorAvailability pickSlot(List<DoctorAvailability> slots, Doctor doctor,
                                               LocalDate date, LocalTime startTime) {
        return slots.stream()
                .filter(slot -> slot.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                        && slot.getSlotDate().equals(date)
                        && slot.getStartTime().equals(startTime))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Seed data expects a slot for Dr " + doctor.getLastName()
                                + " on " + date + " at " + startTime));
    }

    private DoctorAvailability makeSlot(Doctor doctor, LocalDate date,
                                         LocalTime start, LocalTime end, int durationMinutes) {
        return new DoctorAvailability()
                .setDoctor(doctor)
                .setSlotDate(date)
                .setStartTime(start)
                .setEndTime(end)
                .setDurationMinutes(durationMinutes)
                .setIsAvailable(true);
    }
}
