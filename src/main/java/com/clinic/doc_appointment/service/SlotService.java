package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.exception.BookedSlotException;
import com.clinic.doc_appointment.exception.InvalidSlotDateException;
import com.clinic.doc_appointment.exception.InvalidSlotTimeException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.exception.SlotOverlapException;
import com.clinic.doc_appointment.mapper.SlotMapper;
import com.clinic.doc_appointment.repository.DoctorAvailabilityRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.security.SelfAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.util.EntityFinder;
import com.clinic.doc_appointment.validation.CompositeValidator;
import com.clinic.doc_appointment.validation.Validator;
import com.clinic.doc_appointment.validation.slot.SlotCreationValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Doctor availability slots.
 *
 * <p>Writes are owner-scoped: the {@code doctorId} arrives in a path variable or request body, so
 * without a check against the caller any signed-in user could fabricate slots on someone else's
 * calendar or wipe a day of it. Reads stay open to any authenticated user, since patients browse
 * availability to book.
 */
@Service
@Slf4j
public class SlotService {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final DoctorAvailabilityRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final SlotMapper slotMapper;
    private final SelfAccessGuard selfAccessGuard;

    /**
     * The single-slot rule chain (date-not-past → time-order → overlap), composed once at startup.
     * It used to be rebuilt on every request.
     */
    private final Validator<CreateSlotRequest> slotCreationRules;

    public SlotService(DoctorAvailabilityRepository slotRepository,
                       DoctorRepository doctorRepository,
                       SlotMapper slotMapper,
                       SelfAccessGuard selfAccessGuard,
                       List<SlotCreationValidator> slotCreationValidators) {
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
        this.slotMapper = slotMapper;
        this.selfAccessGuard = selfAccessGuard;
        this.slotCreationRules = new CompositeValidator<>(slotCreationValidators);
    }

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request, UserPrincipal caller) {
        selfAccessGuard.assertDoctorSelf(caller, request.getDoctorId());
        log.info("Creating slot for doctor: {} on {}", request.getDoctorId(), request.getSlotDate());

        Doctor doctor = requireDoctor(request.getDoctorId());

        slotCreationRules.validate(request);

        DoctorAvailability slot = new DoctorAvailability()
                .setDoctor(doctor)
                .setSlotDate(request.getSlotDate())
                .setStartTime(request.getStartTime())
                .setEndTime(request.getEndTime())
                .setDurationMinutes(request.getDurationMinutes())
                .setIsAvailable(true);

        DoctorAvailability savedSlot = slotRepository.save(slot);
        log.info("Slot created successfully: {}", savedSlot.getSlotId());

        return slotMapper.toResponse(savedSlot);
    }

    /**
     * Fills a day with back-to-back slots.
     *
     * <p>The walk over the day is done in <strong>minutes from midnight</strong> rather than by
     * repeatedly calling {@code LocalTime.plusMinutes}. That is not a style preference:
     * {@code LocalTime} wraps at midnight, so with a day ending at 23:59 the old loop reached
     * 23:00, computed an end of 00:00, found that 00:00 is not after 23:59, and walked the entire
     * day again — forever, growing a list and issuing a database query on every iteration. An
     * integer counter cannot wrap, so the loop is bounded by construction.
     *
     * <p>Overlap is resolved against <strong>one</strong> query for the day instead of one query
     * per candidate slot. Candidates that clash with an existing slot are skipped rather than
     * rejected, so a doctor can extend a partly-filled day — but a request where <em>every</em>
     * candidate clashes is a conflict, not a success, and no longer reports "0 slots created".
     */
    @Transactional
    public List<SlotResponse> createBulkSlots(BulkSlotRequest request, UserPrincipal caller) {
        selfAccessGuard.assertDoctorSelf(caller, request.getDoctorId());
        log.info("Creating bulk slots for doctor: {} on {}", request.getDoctorId(), request.getSlotDate());

        Doctor doctor = requireDoctor(request.getDoctorId());

        // Day-level rules, throwing the same exceptions the single-slot validators use. The overlap
        // rule is deliberately not applied to the whole window - see the skip behaviour below.
        if (request.getSlotDate().isBefore(LocalDate.now())) {
            throw new InvalidSlotDateException("Cannot create slots for past dates");
        }
        if (!request.getDayEndTime().isAfter(request.getDayStartTime())) {
            throw new InvalidSlotTimeException("Day end time must be after start time");
        }

        int duration = request.getSlotDurationMinutes();
        int breakMinutes = request.getBreakDurationMinutes() == null ? 0 : request.getBreakDurationMinutes();
        if (duration <= 0) {
            throw new InvalidSlotTimeException("Slot duration must be a positive number of minutes");
        }

        int dayStart = minutesFromMidnight(request.getDayStartTime());
        int dayEnd = minutesFromMidnight(request.getDayEndTime());

        // One query for the whole day; overlap is then a pure in-memory comparison.
        List<DoctorAvailability> existing =
                slotRepository.findByDoctorDoctorIdAndSlotDate(request.getDoctorId(), request.getSlotDate());

        List<DoctorAvailability> toCreate = new ArrayList<>();
        int skipped = 0;

        for (int start = dayStart; start + duration <= dayEnd; start += duration + breakMinutes) {
            LocalTime slotStart = atMinute(start);
            LocalTime slotEnd = atMinute(start + duration);

            if (overlapsAny(existing, slotStart, slotEnd)) {
                skipped++;
                continue;
            }

            toCreate.add(new DoctorAvailability()
                    .setDoctor(doctor)
                    .setSlotDate(request.getSlotDate())
                    .setStartTime(slotStart)
                    .setEndTime(slotEnd)
                    .setDurationMinutes(duration)
                    .setIsAvailable(true));
        }

        if (toCreate.isEmpty()) {
            throw new SlotOverlapException(skipped > 0
                    ? "Every slot in this range overlaps an existing slot. Nothing was created."
                    : "The requested range is too short to fit a single slot of "
                            + duration + " minutes.");
        }

        List<DoctorAvailability> savedSlots = slotRepository.saveAll(toCreate);
        log.info("Created {} slots for doctor {} on {} ({} skipped as overlapping)",
                savedSlots.size(), request.getDoctorId(), request.getSlotDate(), skipped);

        return slotMapper.toResponseList(savedSlots);
    }

    // =============== READS ===============

    @Transactional(readOnly = true)
    public SlotResponse getSlotById(String slotId) {
        DoctorAvailability slot = EntityFinder.findOrThrow(slotRepository, slotId,
                "Slot not found with ID: " + slotId);
        return slotMapper.toResponse(slot);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlotsByDoctor(String doctorId) {
        assertDoctorExists(doctorId);
        return slotMapper.toResponseList(
                slotRepository.findBookableSlotsByDoctor(doctorId, LocalDate.now(), LocalTime.now()));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        assertDoctorExists(doctorId);
        return slotMapper.toResponseList(
                slotRepository.findBookableSlotsByDoctorAndDate(doctorId, date, LocalDate.now(), LocalTime.now()));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlotsByDoctorAndDateRange(
            String doctorId, LocalDate startDate, LocalDate endDate) {

        assertDoctorExists(doctorId);
        return slotMapper.toResponseList(slotRepository.findBookableSlotsByDoctorAndDateRange(
                doctorId, startDate, endDate, LocalDate.now(), LocalTime.now()));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAllSlotsByDoctorAndDate(String doctorId, LocalDate date) {
        assertDoctorExists(doctorId);
        return slotMapper.toResponseList(slotRepository.findByDoctorDoctorIdAndSlotDate(doctorId, date));
    }

    // =============== DELETES ===============

    @Transactional
    public void deleteSlot(String slotId, UserPrincipal caller) {
        log.info("Deleting slot: {}", slotId);

        DoctorAvailability slot = EntityFinder.findOrThrow(slotRepository, slotId,
                "Slot not found with ID: " + slotId);

        // Ownership comes from the slot itself, not from anything the caller supplied.
        selfAccessGuard.assertDoctorSelf(caller, slot.getDoctor().getDoctorId());

        if (!slot.getIsAvailable()) {
            throw new BookedSlotException("Cannot delete a booked slot");
        }

        slotRepository.delete(slot);
        log.info("Slot deleted successfully: {}", slotId);
    }

    @Transactional
    public void deleteSlotsByDoctorAndDate(String doctorId, LocalDate date, UserPrincipal caller) {
        selfAccessGuard.assertDoctorSelf(caller, doctorId);
        log.info("Deleting slots for doctor: {} on date: {}", doctorId, date);

        // Lock the date's slots (SELECT ... FOR UPDATE) so a concurrent booking cannot
        // flip availability between this check and the delete (TOCTOU guard).
        List<DoctorAvailability> slots = slotRepository.findByDoctorAndDateForUpdate(doctorId, date);

        boolean hasBookedSlots = slots.stream().anyMatch(slot -> !slot.getIsAvailable());
        if (hasBookedSlots) {
            throw new BookedSlotException("Cannot delete slots that are already booked");
        }

        // Delete exactly the locked, inspected rows (versioned entity deletes).
        slotRepository.deleteAll(slots);
        log.info("Deleted all slots for doctor: {} on date: {}", doctorId, date);
    }

    // =============== HELPERS ===============

    private Doctor requireDoctor(String doctorId) {
        return EntityFinder.findOrThrow(doctorRepository, doctorId, doctorNotFound(doctorId));
    }

    private void assertDoctorExists(String doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException(doctorNotFound(doctorId));
        }
    }

    private static String doctorNotFound(String doctorId) {
        return "Doctor not found with ID: " + doctorId;
    }

    private static int minutesFromMidnight(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    private static LocalTime atMinute(int minuteOfDay) {
        return LocalTime.ofSecondOfDay(Math.min(minuteOfDay, MINUTES_PER_DAY - 1) * 60L);
    }

    /** Half-open interval intersection — the same predicate the overlap query uses in SQL. */
    private static boolean overlapsAny(List<DoctorAvailability> existing, LocalTime start, LocalTime end) {
        return existing.stream().anyMatch(
                slot -> slot.getStartTime().isBefore(end) && slot.getEndTime().isAfter(start));
    }
}
