package com.silzila.payload.response;

import com.silzila.model.base.ModelBase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class RefreshTokenResponse extends ModelBase {

    private static final long serialVersionUID = 7561193836933783650L;

    private String accessToken;

}
