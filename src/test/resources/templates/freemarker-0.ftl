{
	"rating": {
	<#list rating as k,v> 
		"${k}": {
			"value": ${v.value}
		}
	<#sep>,</#sep>
	</#list>
	},
	"new-entries": [
		<#list rating as k,v> 
		{
			"${k}": ${v.value}
		}
		<#sep>,</#sep>
		</#list>
	]
}