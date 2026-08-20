<script lang="ts">
	let { 
		value = $bindable(''), 
		disabled = false, 
		hasError = false, 
		onEnter
	}: {
		value: string;
		disabled?: boolean;
		hasError?: boolean;
		onEnter?: () => void;
	} = $props();

	function handleKeydown(event: KeyboardEvent) {
		if (event.key === 'Enter' && onEnter) {
			onEnter();
		}
	}
</script>

<div
	class="input-bordered input w-full input-primary flex items-center gap-0"
	class:input-error={hasError}
>
	<span class="select-none">wss://</span>
	<input
		type="search"
		name="url_input"
		placeholder="hostname:port"
		class="grow bg-transparent outline-none"
		style="text-transform: lowercase;"
		autocapitalize="none"
		autocorrect="off"
		spellcheck="false"
		bind:value
		{disabled}
		onkeydown={handleKeydown}
	/>
</div>
