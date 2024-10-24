package com.libraryman_api.member;

import com.libraryman_api.exception.InvalidPasswordException;
import com.libraryman_api.exception.InvalidSortFieldException;
import com.libraryman_api.exception.ResourceNotFoundException;
import com.libraryman_api.member.dto.MembersDto;
import com.libraryman_api.member.dto.UpdateMembersDto;
import com.libraryman_api.member.dto.UpdatePasswordDto;
import com.libraryman_api.notification.NotificationService;
import com.libraryman_api.security.config.PasswordEncoder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;

import java.util.Optional;


/**
 * Service class responsible for managing member-related operations in the LibraryMan system.
 *
 * <p>This service provides methods for retrieving, adding, updating, and deleting member records.
 * It integrates with the {@link NotificationService} to send notifications related to member
 * activities such as account creation, updates, and deletions.</p>
 *
 * <p>Each method interacts with the {@link MemberRepository} to perform database operations, ensuring
 * proper transactional behavior and consistency.</p>
 *
 * <p>In cases where a member record is not found, the service throws a
 * {@link ResourceNotFoundException} to indicate the error.</p>
 *
 * @author Ajay Negi
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs a new {@code MemberService} with the specified repositories and services.
     *
     * @param memberRepository    the repository for managing member records
     * @param notificationService the service for sending notifications related to member activities
     */
    public MemberService(MemberRepository memberRepository, NotificationService notificationService, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Retrieves a paginated list of all members from the database.
     *
     * @param pageable the pagination information, including the page number and size
     * @return a {@link Page} of {@link Members} representing all members
     * @throws InvalidSortFieldException if an invalid sortBy field is specified
     */
    public Page<MembersDto> getAllMembers(Pageable pageable) {
        try {
            Page<Members> pagedMembers = memberRepository.findAll(pageable);
            return pagedMembers.map(this::EntityToDto);
        } catch (PropertyReferenceException ex) {
            throw new InvalidSortFieldException("The specified 'sortBy' value is invalid.");
        }
    }

    /**
     * Retrieves a member record by its ID.
     *
     * @param memberId the ID of the member to retrieve
     * @return an {@code Optional} containing the found member, or {@code Optional.empty()} if no member was found
     */

    @Cacheable(value = "members", key = "#memberId")
    public Optional<MembersDto> getMemberById(int memberId) {

        Optional<Members> memberById = memberRepository.findById(memberId);
        return memberById.map(this::EntityToDto);
    }

    /**
     * Adds a new member to the library system.
     *
     * <p>This method saves the new member record in the database and sends a notification
     * about the account creation.</p>
     *
     * @param membersDto the member details to be added
     * @return the saved member record
     */
    public MembersDto addMember(MembersDto membersDto) {
        Members member = DtoEntity(membersDto);
        Members currentMember = memberRepository.save(member);
        if (currentMember != null)
            notificationService.accountCreatedNotification(currentMember);

        return EntityToDto(currentMember);
    }

    /**
     * Updates an existing member's details.
     *
     * <p>This method updates the member's details in the database. It throws a
     * {@link ResourceNotFoundException} if the member is not found. After updating,
     * a notification about the account details update is sent.</p>
     *
     * @param memberId          the ID of the member to update
     * @param membersDtoDetails the updated member details
     * @return the updated member record
     * @throws ResourceNotFoundException if the member is not found
     */

    @CacheEvict(value = "members", key = "#memberId")
    public MembersDto updateMember(int memberId, UpdateMembersDto membersDtoDetails) {
        Members member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        member.setName(membersDtoDetails.getName());
        member.setUsername(membersDtoDetails.getUsername());
        member.setEmail(membersDtoDetails.getEmail());
        member = memberRepository.save(member);
        if (member != null)
            notificationService.accountDetailsUpdateNotification(member);
        return EntityToDto(member);
    }

    /**
     * Deletes a member from the library system.
     *
     * <p>This method deletes the member record from the database after ensuring that
     * the member has no outstanding fines or borrowed books. Before deletion, it
     * sends a notification about the account deletion.</p>
     *
     * @param memberId the ID of the member to delete
     * @throws ResourceNotFoundException if the member is not found
     */
    @CacheEvict(value = "members", key = "#memberId")
    public void deleteMember(int memberId) {
        Members member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        // TODO: Implement logic to check if the member has any outstanding fines or borrowed books.
        // If there are no pending obligations, delete all related notifications, borrowings, and fines.

        notificationService.accountDeletionNotification(member);
        memberRepository.delete(member);
    }

    /**
     * Updates the password for a library member.
     *
     * <p>This method verifies the current password provided by the member, checks if the
     * new password is different, and then updates the member's password in the database.
     * If the current password is incorrect or the new password is the same as the current
     * password, an {@link InvalidPasswordException} is thrown.</p>
     *
     * @param memberId          the ID of the member whose password is to be updated
     * @param updatePasswordDto the {@link UpdatePasswordDto} object containing the password details
     * @throws ResourceNotFoundException if the member with the specified ID is not found
     * @throws InvalidPasswordException  if the current password is incorrect or the new password is the same as the current password
     */
    public void updatePassword(int memberId, UpdatePasswordDto updatePasswordDto) {
        Members member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        // Check the current password
        String currentAuthPassword = member.getPassword();

        if (!passwordEncoder.bCryptPasswordEncoder().matches(updatePasswordDto.getCurrentPassword(), currentAuthPassword)) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        // Check if new password is different from old password
        if (updatePasswordDto.getCurrentPassword().equals(updatePasswordDto.getNewPassword())) {
            throw new InvalidPasswordException("New password must be different from the old password");
        }

        member.setPassword(passwordEncoder.bCryptPasswordEncoder().encode(updatePasswordDto.getNewPassword()));
        memberRepository.save(member);
    }

    /**
     * Converts a MembersDto object to a Members entity.
     *
     * <p>This method takes a MembersDto object and transforms it into a Members entity
     * to be used in database operations. It maps all relevant member details from
     * the DTO, including member ID, role, name, username, email, password, and membership date.</p>
     *
     * @param membersDto the DTO object containing member information
     * @return a Members entity with data populated from the DTO
     */
    public Members DtoEntity(MembersDto membersDto) {
        Members members = new Members();
        members.setMemberId(membersDto.getMemberId());
        members.setRole(membersDto.getRole());
        members.setName(membersDto.getName());
        members.setUsername(membersDto.getUsername());
        members.setEmail(membersDto.getEmail());
        members.setPassword(membersDto.getPassword());
        members.setMembershipDate(membersDto.getMembershipDate());
        return members;
    }

    /**
     * Converts a Members entity to a MembersDto object.
     *
     * <p>This method takes a Members entity object and converts it into a MembersDto
     * object to be used for data transfer between layers. It maps all necessary
     * member details, including member ID, name, username, role, email, password, and membership
     * date, from the entity to the DTO.</p>
     *
     * @param members the entity object containing member information
     * @return a MembersDto object with data populated from the entity
     */
    public MembersDto EntityToDto(Members members) {
        MembersDto membersDto = new MembersDto();
        membersDto.setMemberId(members.getMemberId());
        membersDto.setName(members.getName());
        membersDto.setUsername(members.getUsername());
        membersDto.setRole(members.getRole());
        membersDto.setEmail(members.getEmail());
        membersDto.setPassword(members.getPassword());
        membersDto.setMembershipDate(members.getMembershipDate());
        return membersDto;
    }
}
