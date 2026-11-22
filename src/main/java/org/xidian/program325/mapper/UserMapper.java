package org.xidian.program325.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.xidian.program325.pojo.Account.Role;
import org.xidian.program325.pojo.Account.User;

@Mapper
public interface UserMapper {
    @Select("SELECT user_id, username, password_hash, role FROM Users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Insert("INSERT INTO Users (username, password_hash, role) VALUES (#{username}, #{passwordHash}, #{role})")
    void insert(@Param("username") String username, @Param("passwordHash") String passwordHash,
                @Param("role") Role role);
}
