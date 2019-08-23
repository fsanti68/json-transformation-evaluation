<#setting number_format="computer">
{
"entries": [
	<#list user.accounts as e>
		<#assign options=false>
		{
			"id": "${e.key}",
			"_ref": ${user.cpf},
			"attribs": {
			<#list e as k,v>
				<#if k != "key">
					"${k}": "${v}"
					<#sep>,</#sep>
				</#if>
			</#list>
			},
		<#if user.signature?? && user.signature.owner == e.key>
			"pk": "${user.signature.pub}",
		</#if>
		"options": [
			<#if e.type == "mobile">
				<#assign options=true>
				"NOPASSWD"
			</#if>
		<#list user.products as p>
			<#if p.account == e.key && p.roles??>
				<#list p.roles as r>
					<#if r != "unused">
						<#if options>,<#assign options=false></#if>
						<#if r == "admin">"MANAGER"<#else>"${r?upper_case}"</#if>
						<#assign options=true>
						</#if>
				</#list>
			</#if>
		</#list>
		]
		}
		<#sep>,</#sep>
	</#list>
]
}
