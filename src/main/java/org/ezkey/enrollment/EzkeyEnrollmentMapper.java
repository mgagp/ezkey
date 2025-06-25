package org.ezkey.enrollment;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EzkeyEnrollmentMapper {

	/**
	 * Fetches all EzkeyEnrollment records.
	 *
	 * @return a list of EzkeyEnrollment objects.
	 */
	@Select("""
			    SELECT
			    	enrollment_id,
			    	integration_id,
			    	enrollment_name,
			    	enrollment_read,
			    	enrollment_confirmed,
			    	enrollment_active,
			        auth_attempt_challenge_required,
			        enrollment_challenge,
			        integration_private_key,
			        integration_public_key,
			        auth_attempt_public_key,
			        enrollment_code,
			        created_at
			    FROM ezkey_enrollment
			""")
	@Results({ //
			@Result(property = "enrollmentId", column = "enrollment_id"), //
			@Result(property = "integrationId", column = "integration_id"), //
			@Result(property = "enrollmentName", column = "enrollment_name"), //
			@Result(property = "enrollmentRead", column = "enrollment_read"), //
			@Result(property = "enrollmentConfirmed", column = "enrollment_confirmed"), //
			@Result(property = "enrollmentActive", column = "enrollment_active"), //
			@Result(property = "enrollmentChallenge", column = "enrollment_challenge"), //
			@Result(property = "authAttemptChallengeRequired", column = "auth_attempt_challenge_required"), //
			@Result(property = "integrationPrivateKey", column = "integration_private_key"), //
			@Result(property = "integrationPublicKey", column = "integration_public_key"), //
			@Result(property = "authAttemptPublicKey", column = "auth_attempt_public_key"), //
			@Result(property = "enrollmentCode", column = "enrollment_code"), //
			@Result(property = "createdAt", column = "created_at") //
	})
	List<EzkeyEnrollmentV0> findAll();

	@Select("""
			    SELECT
			    	enrollment_id,
			    	integration_id,
			    	enrollment_name,
			    	enrollment_read,
			    	enrollment_confirmed,
			    	enrollment_active,
			        auth_attempt_challenge_required,
			        enrollment_challenge,
			        integration_private_key,
			        integration_public_key,
			        auth_attempt_public_key,
			        enrollment_code,
			        created_at
			    FROM ezkey_enrollment
			    WHERE enrollment_id = #{enrollmentId}
			""")
	@Results({ //
			@Result(property = "enrollmentId", column = "enrollment_id"), //
			@Result(property = "integrationId", column = "integration_id"), //
			@Result(property = "enrollmentName", column = "enrollment_name"), //
			@Result(property = "enrollmentRead", column = "enrollment_read"), //
			@Result(property = "enrollmentConfirmed", column = "enrollment_confirmed"), //
			@Result(property = "enrollmentActive", column = "enrollment_active"), //
			@Result(property = "enrollmentChallenge", column = "enrollment_challenge"), //
			@Result(property = "authAttemptChallengeRequired", column = "auth_attempt_challenge_required"), //
			@Result(property = "integrationPrivateKey", column = "integration_private_key"), //
			@Result(property = "integrationPublicKey", column = "integration_public_key"), //
			@Result(property = "authAttemptPublicKey", column = "auth_attempt_public_key"), //
			@Result(property = "enrollmentCode", column = "enrollment_code"), //
			@Result(property = "createdAt", column = "created_at") //
	})
	EzkeyEnrollmentV0 findById(Integer id);

	@Insert("""
			    INSERT INTO ezkey_enrollment (
			        integration_id,
			        enrollment_name,
			        enrollment_read,
			        enrollment_confirmed,
			        enrollment_active,
			        enrollment_challenge,
			        auth_attempt_challenge_required,
			        integration_private_key,
			        integration_public_key,
			        auth_attempt_public_key,
			        enrollment_code,
			        created_at
			    ) VALUES (
			        #{integrationId},
			        #{enrollmentName},
			        #{enrollmentRead},
			        #{enrollmentConfirmed},
			        #{enrollmentActive},
			        #{enrollmentChallenge},
			        #{authAttemptChallengeRequired},
			        #{integrationPrivateKey},
			        #{integrationPublicKey},
			        #{authAttemptPublicKey},
			        #{enrollmentCode},
			        #{createdAt}
			    )
			""")
	@Options(useGeneratedKeys = true, keyProperty = "enrollmentId")
	int insert(EzkeyEnrollmentV0 enrollment);

	@Update("""
			    UPDATE ezkey_enrollment
			    SET
			        integration_id = #{integrationId},
			        enrollment_read = #{enrollmentRead},
			        enrollment_confirmed = #{enrollmentConfirmed},
			        enrollment_active = #{enrollmentActive},
			   		enrollment_challenge = #{enrollmentChallenge},
			        auth_attempt_challenge_required = #{authAttemptChallengeRequired},
			        integration_private_key = #{integrationPrivateKey},
			        integration_public_key = #{integrationPublicKey},
			        auth_attempt_public_key = #{authAttemptPublicKey},
			        enrollment_code = #{enrollmentCode}
			    WHERE enrollment_id = #{enrollmentId}
			""")
	int update(EzkeyEnrollmentV0 enrollment);

	@Update("UPDATE ezkey_enrollment SET enrollment_read = TRUE WHERE enrollment_id = #{id}")
	int setDeviceReadTrue(Integer id);

	@Delete("DELETE FROM ezkey_enrollment WHERE id = #{id}")
	int delete(Integer id);
}