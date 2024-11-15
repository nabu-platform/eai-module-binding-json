/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package nabu.data.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.datastore.DatastoreOutputStream;
import be.nabu.libs.datastore.api.ContextualWritableDatastore;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.structure.StructureGenerator;

@WebService
public class Services {
	
	private ExecutionContext context;
	private ServiceRuntime runtime;
	
	@WebResult(name = "unmarshalled")
	public Object unmarshal(@WebParam(name = "input") @NotNull InputStream input, @NotNull @WebParam(name = "type") String type, @WebParam(name = "charset") Charset charset, @WebParam(name = "windows") List<Window> windows, @WebParam(name = "ignoreUndefinedFields") Boolean ignoreUndefinedFields) throws IOException, ParseException {
		ComplexType resolve = (ComplexType) EAIResourceRepository.getInstance().resolve(type);
		if (resolve == null) {
			resolve = (ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve(type);
		}
		JSONBinding binding = new JSONBinding(resolve, charset == null ? Charset.defaultCharset() : charset);
		binding.setIgnoreRootIfArrayWrapper(true);
		binding.setAllowDynamicElements(true);
		binding.setComplexTypeGenerator(new StructureGenerator());
		binding.setIgnoreUnknownElements(ignoreUndefinedFields != null && ignoreUndefinedFields);
		return binding.unmarshal(input, windows == null || windows.isEmpty() ? new Window[0] : windows.toArray(new Window[windows.size()]));
	}
	
	@SuppressWarnings("unchecked")
	@WebResult(name = "marshalled")
	public InputStream marshal(@WebParam(name = "data") @NotNull Object data, @WebParam(name = "charset") Charset charset, @WebParam(name = "prettyPrint") Boolean prettyPrint, @WebParam(name = "ignoreDynamicNames") Boolean ignoreDynamicNames) throws IOException {
		ComplexContent complexContent = data instanceof ComplexContent ? (ComplexContent) data : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(data);
		JSONBinding binding = new JSONBinding(complexContent.getType(), charset == null ? Charset.defaultCharset() : charset);
		if (prettyPrint != null) {
			binding.setPrettyPrint(prettyPrint);
		}
		else {
			binding.setPrettyPrint(true);
		}
		if (ignoreDynamicNames != null) {
			binding.setIgnoreDynamicNames(ignoreDynamicNames);
		}
		binding.setExpandKeyValuePairs(true);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		binding.marshal(output, complexContent);
		return new ByteArrayInputStream(output.toByteArray());
	}
	
	@SuppressWarnings("unchecked")
	@WebResult(name = "uri")
	public URI store(@WebParam(name = "data") Object data, @WebParam(name = "charset") Charset charset, @WebParam(name = "context") String context, @WebParam(name = "name") String name, @WebParam(name = "prettyPrint") Boolean prettyPrint, @WebParam(name = "ignoreDynamicNames") Boolean ignoreDynamicNames) throws URISyntaxException, IOException {
		if (data == null) {
			return null;
		}
		ComplexContent complexContent = data instanceof ComplexContent ? (ComplexContent) data : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(data);
		JSONBinding binding = new JSONBinding(complexContent.getType(), charset == null ? Charset.defaultCharset() : charset);
		if (prettyPrint != null) {
			binding.setPrettyPrint(prettyPrint);
		}
		else {
			binding.setPrettyPrint(true);
		}
		if (ignoreDynamicNames != null) {
			binding.setIgnoreDynamicNames(ignoreDynamicNames);
		}
		DatastoreOutputStream streamable = nabu.frameworks.datastore.Services.streamable(runtime, context, name == null ? complexContent.getType().getName() + ".json" : name, "application/json");
		if (streamable != null) {
			try {
				binding.marshal(streamable, data instanceof ComplexContent ? (ComplexContent) data : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(data));
			}
			finally {
				streamable.close();
			}
			return streamable.getURI();
		}
		else {
			InputStream marshal = marshal(data, charset, prettyPrint, ignoreDynamicNames);
			ContextualWritableDatastore<String> datastore = nabu.frameworks.datastore.Services.getAsDatastore(this.context);
			return datastore.store(context, marshal, name == null ? complexContent.getType().getName() + ".json" : name, "application/json");
		}
	}
}
