package org.ezkey.authattempt;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EzkeyAuthAttemptMapper {

	@Select("""
			    SELECT
			        auth_attempt_id,
			        enrollment_id,
			        auth_attempt_read,
			        auth_attempt_replied,
						        auth_attempt_accepted,
						        auth_attempt_challenge,
			        auth_attempt_code,
			        created_at
			    FROM ezkey_auth_attempt
			    WHERE auth_attempt_id = #{authAttemptId}
			""")

	@Results(id = "EzkeyAuthAttemptResultMap", value = { //
			@Result(property = "authAttemptId", column = "auth_attempt_id"), @Result(property = "enrollmentId", column = "enrollment_id"), //
			@Result(property = "authAttemptRead", column = "auth_attempt_read"), //
			@Result(property = "authAttemptReplied", column = "auth_attempt_replied"), //
			@Result(property = "authAttemptAccepted", column = "auth_attempt_accepted"), //
			@Result(property = "authAttemptChallenge", column = "auth_attempt_challenge"), //
			@Result(property = "authAttemptCode", column = "auth_attempt_code"), @Result(property = "createdAt", column = "created_at") //
	})
	EzkeyAuthAttempt findById(Integer authAttemptId);

	@Select("SELECT * FROM ezkey_auth_attempt")
	@ResultMap("EzkeyAuthAttemptResultMap")
	List<EzkeyAuthAttempt> findAll();

	@Insert("""
			    INSERT INTO ezkey_auth_attempt (
			        enrollment_id,
			        auth_attempt_read,
			        auth_attempt_replied,
			        auth_attempt_accepted,
			        auth_attempt_challenge,
			        auth_attempt_code,
			        created_at
			    ) VALUES (
			        #{enrollmentId},
			        #{authAttemptRead},
			        #{authAttemptReplied},
			        #{authAttemptAccepted},
			        #{authAttemptChallenge},
			        #{authAttemptCode},
			        #{createdAt}
			    )
			""")
	@Options(useGeneratedKeys = true, keyProperty = "authAttemptId")
	int insert(EzkeyAuthAttempt authAttempt);

	@Update("""
			    UPDATE ezkey_auth_attempt
			    SET
			        enrollment_id = #{enrollmentId},
			        auth_attempt_replied = #{authAttemptReplied},
			        auth_attempt_accepted = #{authAttemptAccepted},
			        auth_attempt_challenge = #{authAttemptChallenge}
			    WHERE auth_attempt_id = #{authAttemptId}
			""")
	int update(EzkeyAuthAttempt authAttempt);

	@Delete("DELETE FROM ezkey_auth_attempt WHERE auth_attempt_id = #{authAttemptId}")
	int delete(Integer authAttemptId);

	@Select("""
			    SELECT
			        auth_attempt_id,
			        enrollment_id,
			        auth_attempt_read,
			        auth_attempt_replied,
			        auth_attempt_accepted,
			        auth_attempt_challenge,
			        auth_attempt_code,
			        created_at
			    FROM
			        ezkey_auth_attempt
			    WHERE
			        enrollment_id = #{enrollmentId}
			    ORDER BY created_at DESC
			    LIMIT 1
			""")
	@ResultMap("EzkeyAuthAttemptResultMap")
	EzkeyAuthAttempt findMostRecentByEnrollmentId(Integer enrollmentId);

	@Update("UPDATE ezkey_auth_attempt SET auth_attempt_read = TRUE WHERE auth_attempt_id = #{authAttemptId} AND auth_attempt_read = FALSE")
	int setDeviceReadTrueIfNotRead(Integer authAttemptId);

	@Update("UPDATE ezkey_auth_attempt SET auth_attempt_replied = TRUE WHERE auth_attempt_id = #{authAttemptId} AND auth_attempt_replied = FALSE")
	int setDeviceRepliedTrueIfNotRead(Integer authAttemptId);
}