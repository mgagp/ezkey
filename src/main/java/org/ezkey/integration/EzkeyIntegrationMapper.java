package org.ezkey.integration;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EzkeyIntegrationMapper {

	@Select("""
			    SELECT * FROM ezkey_integration WHERE integration_id = #{id}
			""")

	@Results(id = "EzkeyIntegrationResultMap", value = { //
			@Result(property = "id", column = "integration_id"), //
			@Result(property = "code", column = "integration_code"), //
			@Result(property = "logo", column = "integration_logo"), //
			@Result(property = "active", column = "integration_active"), //
			@Result(property = "createdAt", column = "created_at"), //
			@Result(property = "i18n", column = "integration_id", //
					many = @Many(select = "org.ezkey.integration.EzkeyIntegrationMapper.findI18nByIntegrationId")) //
	})

	EzkeyIntegration findById(Integer id);

	@Select("""
			    SELECT * FROM ezkey_integration
			""")
	@ResultMap("EzkeyIntegrationResultMap")
	List<EzkeyIntegration> findAll();

	@Insert("""
			    INSERT INTO ezkey_integration
			    (
			    	integration_code,
			    	integration_logo,
			    	integration_active,
			    	created_at
			    )
			    VALUES (#{code}, #{logo}, #{active}, #{createdAt})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(EzkeyIntegration integration);

	@Update("""
			    UPDATE ezkey_integration
			    SET
			    	integration_code=#{code},
			    	integration_logo=#{logo},
			    	integration_active=#{integrationActive}
			    WHERE integration_id=#{id}
			""")
	int update(EzkeyIntegration integration);

	@Delete("""
			    DELETE FROM ezkey_integration WHERE integration_id=#{id}
			""")
	int delete(Integer id);

	@Delete("""
			    DELETE FROM ezkey_integration_i18n
			""")
	int deleteAllI18n();

	@Delete("""
			    DELETE FROM ezkey_integration
			""")
	int deleteAll();

	@Select("""
			    SELECT * FROM ezkey_integration_i18n WHERE integration_id = #{integrationId}
			""")
	@Results({ //
			@Result(property = "id", column = "integration_i18n_id"), //
			@Result(property = "integrationId", column = "integration_id"), //
			@Result(property = "language", column = "integration_i18n_lang"), //
			@Result(property = "name", column = "integration_i18n_name"), //
			@Result(property = "description", column = "integration_i18n_description") //
	})
	List<EzkeyIntegrationI18nDto> findI18nByIntegrationId(Integer integrationId);

	@Insert("""
			    INSERT INTO ezkey_integration_i18n
			    (
			    	integration_id,
			    	integration_i18n_lang,
			    	integration_i18n_name,
			    	integration_i18n_description
			    )
			    VALUES (#{integrationId}, #{language}, #{name}, #{description})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertI18n(EzkeyIntegrationI18nDto i18n);

	@Update("""
			    UPDATE ezkey_integration_i18n
			    SET lang=#{language}, name=#{name}, description=#{description}
			    WHERE integration_id=#{id}
			""")
	int updateI18n(EzkeyIntegrationI18nDto i18n);

	@Delete("""
			    DELETE FROM ezkey_integration_i18n WHERE integration_id=#{id}
			""")
	int deleteI18n(Integer id);

}